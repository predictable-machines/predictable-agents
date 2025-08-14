package predictable.mcp.registry.docker

import arrow.fx.coroutines.parMap
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import predictable.mcp.config.MCPConfig
import predictable.mcp.config.MCPServer
import predictable.mcp.config.ServerConfig
import predictable.mcp.registry.MCPRegistry
import java.io.File

class MCPDockerHubRegistry(
  val cacheDir: File,
) : MCPRegistry {

  private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    prettyPrint = true
  }

  val client = HttpClient {
    install(ContentNegotiation) {
      json(json)
    }

    // Logging is disabled due to a missing dependency
    defaultRequest {
      contentType(ContentType.Application.Json)
      accept(ContentType.Application.Json)
    }
  }

  override suspend fun configuration(): MCPConfig {
    // try from cache first otherwise fetch from docker hub and store serialized for next run
    val cacheFile = File(cacheDir, "mcp_registry.json")
    return if (cacheFile.exists()) {
      json.decodeFromString(MCPConfig.serializer(), cacheFile.readText())
    } else {
      val config = fetchFromDockerHub()
      cacheFile.writeText(json.encodeToString(MCPConfig.serializer(), config))
      config
    }
  }

  private suspend fun fetchFromDockerHub(): MCPConfig {
    val response = client.get("https://hub.docker.com/v2/repositories/mcp/") {
      parameter("page_size", 10000)
    }
    val mcpDockerHubResponse = json.decodeFromString<McpDockerHubResponse>(response.bodyAsText())
    val repositoryConfigs = mcpDockerHubResponse.results.parMap(
      concurrency = 5
    ) { repository ->
      val repositoryDetail = repositoryDetail(repository)
      val config = repositoryDetail?.let { mcpServersConfig(it) }
      if (repositoryDetail != null) {
        config?.mcpServers.orEmpty().mapNotNull { server ->
          val serverConfig = server.value
          val command = serverConfig.command
          val args = serverConfig.args
          val env = serverConfig.env ?: emptyMap()
          val key = "${repositoryDetail.name}/${server.key}"
          key to MCPServer(
            name = repositoryDetail.name,
            namespace = repositoryDetail.namespace,
            description = repositoryDetail.description, config = ServerConfig.STDIO(
              command = command,
              args = args, //add user id + tool name as container name
              env = env,
              alwaysAllow = emptyList(),
              disabled = false
            )
          )
        }
      } else emptyList()
    }.flatten().toMap()
    return MCPConfig(repositoryConfigs)
  }

  private fun mcpServersConfig(repositoryDetail: RepositoryDetail): McpServersConfig? {
    try {
      val configJson = repositoryDetail.fullDescription?.substringAfter("```json")
        ?.substringBefore("```")
      return configJson?.let {
        json.decodeFromString<McpServersConfig>(it)
      }
    } catch (e: Exception) {
      return null
    }
  }

  private suspend fun repositoryDetail(repository: Repository): RepositoryDetail? = try {
    val response = client.get("https://hub.docker.com/v2/repositories/${repository.namespace}/${repository.name}") {
      parameter("page_size", 10000)
    }
    val repositoryDetail = json.decodeFromString<RepositoryDetail>(response.bodyAsText())
    repositoryDetail
  } catch (e: Exception) {
    null
  }
}


@Serializable
data class McpDockerHubResponse(
  val count: Int,
  val next: String? = null,
  val previous: String? = null,
  val results: List<Repository>
)

@Serializable
data class Repository(
  val name: String,
  val namespace: String,
  @SerialName("repository_type") val repositoryType: String? = null,
  val status: Int,
  @SerialName("status_description") val statusDescription: String,
  val description: String,
  @SerialName("is_private") val isPrivate: Boolean,
  @SerialName("star_count") val starCount: Int,
  @SerialName("pull_count") val pullCount: Int,
  @SerialName("last_updated") val lastUpdated: String,
  @SerialName("last_modified") val lastModified: String,
  @SerialName("date_registered") val dateRegistered: String,
  val affiliation: String,
  @SerialName("media_types") val mediaTypes: List<String>,
  @SerialName("content_types") val contentTypes: List<String>,
  val categories: List<Category>,
  @SerialName("storage_size") val storageSize: Long
)

@Serializable
data class Category(
  val name: String,
  val slug: String
)


@Serializable
data class RepositoryDetail(
  val user: String,
  val name: String,
  val namespace: String,
  @SerialName("repository_type") val repositoryType: String? = null,
  val status: Int,
  @SerialName("status_description") val statusDescription: String,
  val description: String,
  @SerialName("is_private") val isPrivate: Boolean,
  @SerialName("is_automated") val isAutomated: Boolean,
  @SerialName("star_count") val starCount: Int,
  @SerialName("pull_count") val pullCount: Int,
  @SerialName("last_updated") val lastUpdated: String,
  @SerialName("last_modified") val lastModified: String,
  @SerialName("date_registered") val dateRegistered: String,
  @SerialName("collaborator_count") val collaboratorCount: Int,
  val affiliation: String? = null,
  @SerialName("hub_user") val hubUser: String,
  @SerialName("has_starred") val hasStarred: Boolean,
  @SerialName("full_description") val fullDescription: String? = null,
  val permissions: Permissions,
  @SerialName("media_types") val mediaTypes: List<String>,
  @SerialName("content_types") val contentTypes: List<String>,
  val categories: List<Category>,
  @SerialName("immutable_tags") val immutableTags: Boolean,
  @SerialName("immutable_tags_rules") val immutableTagsRules: String,
  @SerialName("immutable_tags_settings") val immutableTagsSettings: ImmutableTagsSettings,
  @SerialName("storage_size") val storageSize: Long
)

@Serializable
data class Permissions(
  val read: Boolean,
  val write: Boolean,
  val admin: Boolean
)

@Serializable
data class ImmutableTagsSettings(
  val enabled: Boolean,
  val rules: List<String>
)

// Additional classes to parse embedded JSON in `full_description`

@Serializable
/** Top-level wrapper for MCP server configurations. */
data class McpServersConfig(
  @SerialName("mcpServers") val mcpServers: Map<String, McpServerConfig>
)

@Serializable
/** Configuration for a single MCP server. */
data class McpServerConfig(
  val command: String,
  val args: List<String>,
  /** Optional environment variables, if provided in JSON. */
  val env: Map<String, String>? = null
)

