package predictable

import com.xemantic.ai.tool.schema.meta.Description
import predictable.agent.StreamResponse
import kotlinx.coroutines.flow.toList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import predictable.TestUtils.workflowWithEmptyState
import kotlinx.serialization.Required
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import predictable.agent.Model

// Define data classes for structured output with tools testing
@Serializable
@SerialName("CalculationRequest")
@Description("IMPORTANT! All fields required")
data class CalculationRequest(
    @Required
    val question: String
)

@Serializable
@SerialName("CalculationResponse")
@Description("IMPORTANT! All fields required")
data class CalculationResponse(
    @Required
    val question: String,
    @Required
    val answer: Int,
    @Required
    val explanation: String
)

@Serializable
@SerialName("WeatherForecastRequest")
@Description("IMPORTANT! All fields required")
data class WeatherForecastRequest(
    @Required
    val location: String
)

@Serializable
@SerialName("WeatherForecastResponse")
@Description("IMPORTANT! All fields required")
data class WeatherForecastResponse(
    @Required
    val location: String,
    @Required
    val forecast: String,
    @Required
    val temperature: String
)

// Reset tracking variables for tools
var calculatorToolInvokedInCombinedTest = false
var lastCalculatorInputInCombinedTest: CalculatorInput? = null
var weatherToolInvokedInCombinedTest = false
var lastWeatherInputInCombinedTest: WeatherInput? = null

// Helper functions for the tools in combined test
fun calculateResultInCombinedTest(input: CalculatorInput): CalculatorOutput {
    calculatorToolInvokedInCombinedTest = true
    lastCalculatorInputInCombinedTest = input

    val result = when (input.operation) {
        Operation.ADD -> input.a + input.b
        Operation.SUBTRACT -> input.a - input.b
        Operation.MULTIPLY -> input.a * input.b
        Operation.DIVIDE -> input.a / input.b
    }

    return CalculatorOutput(result)
}

fun getWeatherInCombinedTest(input: WeatherInput): String {
    weatherToolInvokedInCombinedTest = true
    lastWeatherInputInCombinedTest = input
    return "The weather in ${input.query} is sunny and 75 degrees."
}

// Create tools for combined testing
val calculatorToolForCombinedTest = Tool(
  name = "Calculator",
  description = "A simple calculator that can add, subtract, multiply, and divide two numbers."
) { input: CalculatorInput -> calculateResultInCombinedTest(input) }

val weatherToolForCombinedTest = Tool(
  name = "Weather",
  description = "Get the current weather for a location."
) { input: WeatherInput -> getWeatherInCombinedTest(input) }

/**
 * This test class tests the combination of structured output and tools in both streaming and non-streaming modes.
 * 
 * Note: The non-streaming tests may fail due to an issue in the OpenAIProvider class.
 * The issue is in the chatCompletionStructured method, which throws an exception when
 * it fails to parse the structured response. This is a known issue and will be fixed in a future update.
 * 
 * The streaming tests demonstrate the combination of structured output and tools in streaming mode.
 */
class AgentStructuredOutputWithToolsTest {

    // Create an agent with tools for testing structured output
    private val testAgentWithTools = Agent(
        name = "Structured Output With Tools Test Agent",
        description = "A test agent for structured output with tools unit tests",
        system = "You are a helpful assistant for testing structured output with tools. Use the tools when appropriate and respond with the exact structured requested in JSON format.",
        model = Model.defaultModel, // Using gpt-4o-mini as required
        tools = listOf(calculatorToolForCombinedTest, weatherToolForCombinedTest),
    )

    @Test
    fun `test structured output with calculator tool`() = workflowWithEmptyState {
        // Reset tracking variables
        calculatorToolInvokedInCombinedTest = false
        lastCalculatorInputInCombinedTest = null

        // Create a structured output generator for CalculationResponse
        val calculationGenerator = testAgentWithTools.generateObject<CalculationRequest, CalculationResponse>()

        // Test the structured output with a calculation request that should trigger the calculator tool
        val request = CalculationRequest(question = "What is 7 multiplied by 8?")
        val response: CalculationResponse = calculationGenerator(request)

        // Verify the response
        assertNotNull(response)
        assertEquals("What is 7 multiplied by 8?", response.question)
        assertTrue(response.explanation.isNotEmpty())

        // Verify that the calculator tool was invoked
        assertTrue(calculatorToolInvokedInCombinedTest, "Calculator tool should have been invoked")
        assertNotNull(lastCalculatorInputInCombinedTest, "Calculator input should not be null")
        assertEquals(Operation.MULTIPLY, lastCalculatorInputInCombinedTest?.operation, "Operation should be 'multiply'")
        assertEquals(7, lastCalculatorInputInCombinedTest?.a, "First number should be 7")
        assertEquals(8, lastCalculatorInputInCombinedTest?.b, "Second number should be 8")
    }

    @Test
    fun `test structured output with weather tool`() = workflowWithEmptyState {
        // Reset tracking variables
        weatherToolInvokedInCombinedTest = false
        lastWeatherInputInCombinedTest = null

        // Create a structured output generator for WeatherForecastResponse
        val weatherGenerator = testAgentWithTools.generateObject<WeatherForecastRequest, WeatherForecastResponse>()

        // Test the structured output with a weather request that should trigger the weather tool
        val request = WeatherForecastRequest(location = "San Francisco")
        val response: WeatherForecastResponse = weatherGenerator(request)

        // Verify the response
        assertNotNull(response)
        assertEquals("San Francisco", response.location)
        assertTrue(response.forecast.isNotEmpty())
        assertTrue(response.temperature.isNotEmpty())

        // Verify that the weather tool was invoked
        assertTrue(weatherToolInvokedInCombinedTest, "Weather tool should have been invoked")
        assertNotNull(lastWeatherInputInCombinedTest, "Weather input should not be null")
        assertEquals("San Francisco", lastWeatherInputInCombinedTest?.query, "Query should be 'San Francisco'")
    }

    @Test
    fun `test streaming structured output with calculator tool`() = workflowWithEmptyState {
        // Reset tracking variables
        calculatorToolInvokedInCombinedTest = false
        lastCalculatorInputInCombinedTest = null

        // Create a structured output streamer for CalculationResponse
        val calculationStreamer = testAgentWithTools.streamObject<CalculationRequest, CalculationResponse>()

        // Test the streaming structured output with a calculation request
        val request = CalculationRequest(question = "What is 12 divided by 3?")
        val streamResponses = calculationStreamer(request).toList()

        // Verify that we got some responses
        assertTrue(streamResponses.isNotEmpty(), "Stream responses should not be empty")

        // Verify that we got an end marker
        val endMarkers = streamResponses.filterIsInstance<StreamResponse.End>()
        assertTrue(endMarkers.isNotEmpty(), "Should have received an end marker")

        // Verify that the calculator tool was invoked
        assertTrue(calculatorToolInvokedInCombinedTest, "Calculator tool should have been invoked")
        assertNotNull(lastCalculatorInputInCombinedTest, "Calculator input should not be null")
        assertEquals(Operation.DIVIDE, lastCalculatorInputInCombinedTest?.operation, "Operation should be 'divide'")
        assertEquals(12, lastCalculatorInputInCombinedTest?.a, "First number should be 12")
        assertEquals(3, lastCalculatorInputInCombinedTest?.b, "Second number should be 3")
    }

    @Test
    fun `test streaming structured output with weather tool`() = workflowWithEmptyState {
        // Reset tracking variables
        weatherToolInvokedInCombinedTest = false
        lastWeatherInputInCombinedTest = null

        // Create a structured output streamer for WeatherForecastResponse
        val weatherStreamer = testAgentWithTools.streamObject<WeatherForecastRequest, WeatherForecastResponse>()

        // Test the streaming structured output with a weather request
        val request = WeatherForecastRequest(location = "Tokyo")
        val streamResponses = weatherStreamer(request).toList()

        // Verify that we got some responses
        assertTrue(streamResponses.isNotEmpty(), "Stream responses should not be empty")

        // Verify that we got an end marker
        val endMarkers = streamResponses.filterIsInstance<StreamResponse.End>()
        assertTrue(endMarkers.isNotEmpty(), "Should have received an end marker")

        // Verify that the weather tool was invoked
        assertTrue(weatherToolInvokedInCombinedTest, "Weather tool should have been invoked")
        assertNotNull(lastWeatherInputInCombinedTest, "Weather input should not be null")
        assertEquals("Tokyo", lastWeatherInputInCombinedTest?.query, "Query should be 'Tokyo'")
    }
}
