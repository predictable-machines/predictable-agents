package predictable.mcp.registry.agent

import predictable.mcp.config.MCPConfig

sealed class Toolset {
  data object DockerHub : Toolset()
  data class Custom(val config: MCPConfig) : Toolset()
}
