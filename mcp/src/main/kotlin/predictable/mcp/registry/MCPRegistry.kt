package predictable.mcp.registry

import predictable.mcp.config.MCPConfig
import predictable.mcp.config.MCPServer
import predictable.mcp.registry.docker.MCPDockerHubRegistry
import java.io.File

interface MCPRegistry {
  suspend fun configuration(): MCPConfig

  suspend operator fun invoke(
    namespace: String,
    name: String,
  ): Pair<String, MCPServer> {
    val config = configuration()
    val server = config.servers.values.firstOrNull { it.name == name && it.namespace == namespace }
      ?: throw IllegalArgumentException("Server $namespace/$name not found in registry")
    return "${namespace}/${name}" to server
  }

  companion object {
    fun dockerHub(
      cacheDir: File = File(".")
    ): MCPDockerHubRegistry = MCPDockerHubRegistry(cacheDir)
  }
}
