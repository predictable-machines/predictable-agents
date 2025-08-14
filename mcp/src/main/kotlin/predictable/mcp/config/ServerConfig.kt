package predictable.mcp.config

import kotlinx.serialization.Serializable

@Serializable
sealed class ServerConfig {
  @Serializable
  data class SSE(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val alwaysAllow: List<String> = emptyList(),
    val disabled: Boolean = false,
  ): ServerConfig()

  @Serializable
  data class STDIO(
    val command: String,
    val args: List<String>,
    val env: Map<String, String> = emptyMap(),
    val alwaysAllow: List<String> = emptyList(),
    val disabled: Boolean = false,
  ) : ServerConfig()
}
