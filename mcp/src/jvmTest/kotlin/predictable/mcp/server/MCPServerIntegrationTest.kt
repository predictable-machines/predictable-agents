package predictable.mcp.server

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import predictable.Tool
import predictable.mcp.client.MCPClient
import predictable.mcp.config.MCPConfig
import predictable.mcp.config.MCPServerConfig
import predictable.mcp.config.ServerConfig
import predictable.mcp.server.MCPServer.startKtorMCPServer
import predictable.tool.KotlinSchema
import kotlin.test.*

/**
 * Integration tests that verify the MCP server can be started,
 * connected to, and that tools can be discovered through the MCP protocol.
 */
class MCPServerIntegrationTest {

  @Serializable
  data class CalculatorInput(val a: Double, val b: Double, val operation: String)

  @Serializable
  data class CalculatorOutput(val result: Double, val operation: String)

  @Serializable
  data class WeatherInput(val city: String)

  @Serializable
  data class WeatherOutput(val temperature: Double, val condition: String, val city: String)

  @Serializable
  data class TextAnalysisInput(val text: String, val analysisType: String)

  @Serializable
  data class TextAnalysisOutput(val wordCount: Int, val charCount: Int, val result: String)

  private fun createTestTools(): List<Tool<*, *>> {
    val calculatorTool = Tool(
      "calculator",
      "Performs basic arithmetic operations",
      KotlinSchema(CalculatorInput.serializer(), CalculatorOutput.serializer())
    ) { input: CalculatorInput ->
      val result = when (input.operation) {
        "add" -> input.a + input.b
        "subtract" -> input.a - input.b
        "multiply" -> input.a * input.b
        "divide" -> if (input.b != 0.0) input.a / input.b else Double.NaN
        else -> 0.0
      }
      CalculatorOutput(result, input.operation)
    }

    val weatherTool = Tool(
      "weather_service",
      "Provides weather information for cities",
      KotlinSchema(WeatherInput.serializer(), WeatherOutput.serializer())
    ) { input: WeatherInput ->
      WeatherOutput(20.0, "Sunny", input.city)
    }

    val textAnalysisTool = Tool(
      "text_analyzer",
      "Analyzes text and provides statistics",
      KotlinSchema(TextAnalysisInput.serializer(), TextAnalysisOutput.serializer())
    ) { input: TextAnalysisInput ->
      TextAnalysisOutput(
        wordCount = input.text.split("\\s+".toRegex()).size,
        charCount = input.text.length,
        result = "Analysis complete for type: ${input.analysisType}"
      )
    }

    return listOf(calculatorTool, weatherTool, textAnalysisTool)
  }

  @Test
  fun testStartKtorMCPServerAndListTools(): Unit = runBlocking {
    // Create tools to expose
    val tools = createTestTools()

    // Start the MCP server using startKtorMCPServer
    val server = startKtorMCPServer(
      tools = tools,
      port = 8090,  // Use specific port for testing
      host = "localhost",
      serverName = "test-mcp-server",
      serverVersion = "1.0.0"
    )

    // Start the server
    server.start(wait = false)

    // Give the server time to fully start
    delay(2000)

    try {
      // Configure MCPClient to connect to our test server
      val config = MCPConfig(
        servers = mapOf(
          "test-server" to MCPServerConfig(
            name = "Test Server",
            namespace = "test",
            description = "Test MCP Server",
            config = ServerConfig.SSE(
              url = "http://localhost:8090/sse"
            )
          )
        )
      )

      // Use MCPClient to connect and test
      MCPClient(config) { client ->
        // List the tools available from the server
        val discoveredTools = client.tools()

        assertEquals(3, discoveredTools.size, "Should discover exactly 3 tools")

        // Verify tool names match what we added
        val toolNames = discoveredTools.map { it.name }.toSet()
        assertTrue(toolNames.contains("calculator"), "Should have calculator tool")
        assertTrue(toolNames.contains("weather_service"), "Should have weather_service tool")
        assertTrue(toolNames.contains("text_analyzer"), "Should have text_analyzer tool")

        // Verify tool descriptions
        val calculatorTool = discoveredTools.find { it.name == "calculator" }
        assertNotNull(calculatorTool)
        assertEquals("Performs basic arithmetic operations", calculatorTool.description)

        val weatherTool = discoveredTools.find { it.name == "weather_service" }
        assertNotNull(weatherTool)
        assertEquals("Provides weather information for cities", weatherTool.description)

        val textTool = discoveredTools.find { it.name == "text_analyzer" }
        assertNotNull(textTool)
        assertEquals("Analyzes text and provides statistics", textTool.description)

        // Test invoking a tool
        val calcInput = buildJsonObject {
          put("a", 10.0)
          put("b", 5.0)
          put("operation", "add")
        }
        val result = calculatorTool.invoke(calcInput)
        assertNotNull(result)
        // Result should contain the calculation result
      }

    } finally {
      // Stop the server
      server.stop(gracePeriodMillis = 0, timeoutMillis = 1000)
    }
  }

  @Test
  fun testMultipleClients() = runBlocking {
    val tools = listOf(
      Tool(
        "test_tool",
        "A simple test tool",
        KotlinSchema(CalculatorInput.serializer(), CalculatorOutput.serializer())
      ) { input: CalculatorInput ->
        CalculatorOutput(42.0, "test")
      }
    )

    val server = startKtorMCPServer(
      tools = tools,
      port = 8091,
      host = "localhost"
    )

    server.start(wait = false)
    delay(2000)

    try {
      val config = MCPConfig(
        servers = mapOf(
          "test-server" to MCPServerConfig(
            name = "Test Server",
            namespace = "test",
            description = "Test MCP Server",
            config = ServerConfig.SSE(
              url = "http://localhost:8091/sse"
            )
          )
        )
      )

      // Test with first client
      MCPClient(config) { client1 ->
        val tools1 = client1.tools()
        assertEquals(1, tools1.size)
        assertEquals("test_tool", tools1[0].name)
      }

      // Test with second client (new connection)
      MCPClient(config) { client2 ->
        val tools2 = client2.tools()
        assertEquals(1, tools2.size)
        assertEquals("test_tool", tools2[0].name)
      }

    } finally {
      server.stop(0, 1000)
    }
  }

  @Test
  fun testEmptyToolsList() = runBlocking {
    // Start server with no tools
    val server = startKtorMCPServer(
      tools = emptyList(),
      port = 8092,
      host = "localhost"
    )

    server.start(wait = false)
    delay(2000)

    try {
      val config = MCPConfig(
        servers = mapOf(
          "empty-server" to MCPServerConfig(
            name = "Empty Server",
            namespace = "empty",
            description = "Server with no tools",
            config = ServerConfig.SSE(
              url = "http://localhost:8092/sse"
            )
          )
        )
      )

      MCPClient(config) { client ->
        val tools = client.tools()
        assertEquals(0, tools.size, "Should have no tools when server started with empty list")

        // Note: Resources capability is not enabled in our server
        // so we don't test resources here
      }

    } finally {
      server.stop(0, 1000)
    }
  }

  @Test
  fun testServerWithCustomConfiguration(): Unit = runBlocking {
    val tools = createTestTools()
    val customName = "custom-test-server"
    val customVersion = "2.5.0"

    val server = startKtorMCPServer(
      tools = tools,
      port = 8093,
      host = "localhost",
      serverName = customName,
      serverVersion = customVersion
    )

    server.start(wait = false)
    delay(2000)

    try {
      val config = MCPConfig(
        servers = mapOf(
          "custom-server" to MCPServerConfig(
            name = "Custom Server",
            namespace = "custom",
            description = "Server with custom configuration",
            config = ServerConfig.SSE(
              url = "http://localhost:8093/sse"
            )
          )
        )
      )

      MCPClient(config) { client ->
        // Just verify we can connect and list tools
        // The server name/version are used internally but not exposed through listTools
        val tools = client.tools()
        assertEquals(3, tools.size)

        // Test tool invocation with weather service
        val weatherTool = tools.find { it.name == "weather_service" }
        assertNotNull(weatherTool)

        val weatherInput = buildJsonObject {
          put("city", "Tokyo")
        }
        val weatherResult = weatherTool.invoke(weatherInput)
        assertNotNull(weatherResult)
      }

    } finally {
      server.stop(0, 1000)
    }
  }
}
