package predictable.mcp.registry.agent

import predictable.agent
import predictable.agent.Model
import predictable.mcp.config.MCPConfig
import predictable.mcp.config.ServerConfig
import predictable.mcp.registry.MCPRegistry

class ToolSelectionAgent(
  private val toolSet: Toolset,
) {

  suspend fun findToolsForTask(
    request: FindToolsRequest,
    includeAuthenticated: Boolean,
    ): MCPConfig {
    val source = when (toolSet) {
      is Toolset.DockerHub -> MCPRegistry.dockerHub().configuration()
      is Toolset.Custom -> toolSet.config
    }
    val toolSelectionAgent = agent(
      name = "Tool Selection Agent",
      description = "Selects the best tools for the task",
      system = """
        You are a tools selection agent. Your job is to select the best tools for the task.
        You will be given a task description and a list of tools.
        You need to select the best tools for the task that would help accomplish the task.
        You can choose from the following tools:
        ${source.servers.values.filter { 
          when (it.config) {
            is ServerConfig.SSE -> true //
            is ServerConfig.STDIO -> includeAuthenticated || it.config.env.isEmpty() // for now only unauthenticated tools
          }
        }.joinToString("\n") { server ->
        """
            
          - name: ${server.name}
          - namespace: ${server.namespace}
          - description: ${server.description}
          
          """.trimIndent()
      }}
      """.trimIndent(),
      model = Model.default,
    )
    val selectedTools: FindToolsResponse = toolSelectionAgent.generateObject(FindToolsRequest(request.query))
    val selectedToolNames = selectedTools.tools.map { it.name }
    val selectedToolNamespaces = selectedTools.tools.map { it.namespace }
    val selectedServers = source.servers.filter { server ->
      selectedToolNames.contains(server.value.name) && selectedToolNamespaces.contains(server.value.namespace)
    }
    val selectedConfig = MCPConfig(
      servers = selectedServers.mapValues { it.value }
    )
    return selectedConfig
  }


}
