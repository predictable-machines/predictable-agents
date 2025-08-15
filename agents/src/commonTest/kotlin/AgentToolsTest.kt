package predictable

import predictable.TestUtils.workflowWithEmptyState
import predictable.agent.StreamResponse
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import predictable.*
import predictable.AI.Companion.unaryPlus
import predictable.agent.Model

enum class Operation {
    ADD,
    SUBTRACT,
    MULTIPLY,
    DIVIDE
}

// Define simple data classes for our tool inputs
@Serializable
data class CalculatorInput(val operation: Operation, val a: Int, val b: Int)

@Serializable
data class WeatherInput(val query: String)

// Define a simple data class for our tool output
@Serializable
data class CalculatorOutput(val result: Int)

// Track if the tools were invoked
var calculatorToolInvoked = false
var lastCalculatorInput: CalculatorInput? = null
var weatherToolInvoked = false
var lastWeatherQuery: String? = null

// Helper functions for the tools
suspend fun calculateResult(input: CalculatorInput): CalculatorOutput {
    calculatorToolInvoked = true
    lastCalculatorInput = input

    val result = when (input.operation) {
        Operation.ADD -> input.a + input.b
        Operation.SUBTRACT -> input.a - input.b
        Operation.MULTIPLY -> input.a * input.b
        Operation.DIVIDE -> input.a / input.b
    }

    return CalculatorOutput(result)
}

suspend fun getWeather(input: WeatherInput): String {
    weatherToolInvoked = true
    lastWeatherQuery = input.query
    return "The weather in ${input.query} is sunny and 75 degrees."
}

class AgentToolsTest {

    // Create an agent with tools for testing
    private val testAgentWithTools = Agent(
        name = "Test Agent With Tools",
        description = "A test agent with tools for unit tests",
        system = "You are a helpful assistant for testing purposes. Use the tools when appropriate.",
        model = Model.default, // Using gpt-4o-mini as required
        tools = listOf(+::calculateResult, +::getWeather),
    )

    @Test
    fun `test agent with calculator tool`() = workflowWithEmptyState {
        // Reset tracking variables
        calculatorToolInvoked = false
        lastCalculatorInput = null

        // Test the invoke method with a prompt that should trigger the calculator tool
        val response: String = testAgentWithTools("What is 5 plus 3?")

        // Verify the response
        assertNotNull(response)
        assertTrue(response.isNotEmpty())
        println("Response: $response")

        // Verify that the calculator tool was invoked
        assertTrue(calculatorToolInvoked, "Calculator tool should have been invoked")
        assertNotNull(lastCalculatorInput, "Calculator input should not be null")
        assertEquals(Operation.ADD, lastCalculatorInput?.operation, "Operation should be 'add'")
        assertEquals(5, lastCalculatorInput?.a, "First number should be 5")
        assertEquals(3, lastCalculatorInput?.b, "Second number should be 3")
    }

    @Test
    fun `test agent with calculator tool using streaming`() = workflowWithEmptyState {
        // Reset tracking variables
        calculatorToolInvoked = false
        lastCalculatorInput = null

        // Test the stream method with a prompt that should trigger the calculator tool
        val streamResponses = testAgentWithTools.stream("What is 5 plus 3?").toList()

        // Verify that we got some responses
        assertTrue(streamResponses.isNotEmpty(), "Stream responses should not be empty")

        // Check if we received a tool call
        val toolCalls = streamResponses.filterIsInstance<StreamResponse.ToolCall>()
        assertTrue(toolCalls.isNotEmpty(), "Should have received at least one tool call")

        // Verify that the calculator tool was invoked
        assertTrue(calculatorToolInvoked, "Calculator tool should have been invoked")
        assertNotNull(lastCalculatorInput, "Calculator input should not be null")
        assertEquals(Operation.ADD, lastCalculatorInput?.operation, "Operation should be 'add'")
        assertEquals(5, lastCalculatorInput?.a, "First number should be 5")
        assertEquals(3, lastCalculatorInput?.b, "Second number should be 3")

        // Verify that we got content chunks after the tool call
        val contentChunks = streamResponses.filterIsInstance<StreamResponse.Chunk<String>>()
        assertTrue(contentChunks.isNotEmpty(), "Should have received content chunks after tool call")

        // Verify that we got an end marker
        val endMarkers = streamResponses.filterIsInstance<StreamResponse.End>()
        assertTrue(endMarkers.isNotEmpty(), "Should have received an end marker")

        // Print the content chunks for debugging
        println("Content chunks: ${contentChunks.joinToString { it.value }}")
    }

}
