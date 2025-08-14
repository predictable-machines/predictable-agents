package predictable.mcp.server

import io.modelcontextprotocol.kotlin.sdk.*
import io.modelcontextprotocol.kotlin.sdk.server.RegisteredTool
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import predictable.AI
import predictable.agent.providers.openai.executeTool
import predictable.tool.InputSchema
import predictable.tool.KotlinSchema
import java.util.UUID

fun mcpServer(tools: List<AI<*, *>>): Server {
  return Server(
    serverInfo = Implementation(
      name = "example-sse-server",
      version = "1.0.0"
    ),
    options = ServerOptions(
      capabilities = ServerCapabilities(
        tools = ServerCapabilities.Tools(
          listChanged = true
        )
      )
    )
  ).also { server ->
    server.addTools(tools.registeredTools())
  }
}

private fun List<AI<*, *>>.registeredTools(): List<RegisteredTool> {
  return this.map { tool ->
    RegisteredTool(
      tool = Tool(
        name = tool.name,
        description = tool.description,
        inputSchema = tool.schema.toolInput(),
      ),
      handler = { request ->
        @Suppress("UNCHECKED_CAST")
        tool as AI<Any?, Any?>
        val input = tool.schema.inputFromJson(request.arguments.toString())
        val id = UUID.randomUUID().toString()
        val output = executeTool(targetTool = tool, input = input, toolCallback = null, id)
        val jsonValue = KotlinSchema.json.encodeToString(output)
        CallToolResult(//TODO handle images and other content types
          content = listOf(
            TextContent(jsonValue)
          )
        )
      }
    )
  }
}

private fun InputSchema<*>.toolInput(): Tool.Input {
  val schema = KotlinSchema.json.parseToJsonElement(inputJsonSchema()).jsonObject
  return Tool.Input(
    properties = schema["properties"]?.jsonObject ?: error("Invalid schema: $schema"),
    required = schema["required"]?.jsonArray?.map { it.toString() },
  ).also {
    println("Tool Input: $it")
  }
}
