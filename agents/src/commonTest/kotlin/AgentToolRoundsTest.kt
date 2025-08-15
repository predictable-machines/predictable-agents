package predictable

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import predictable.AI.Companion.unaryPlus
import predictable.TestUtils.workflowWithEmptyState
import predictable.agent.Model
import predictable.agent.RequestParameters
import predictable.agent.StreamResponse
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// Define a counter to track the number of tool invocations
var calculatorInvocationCount = 0
var weatherInvocationCount = 0
var randomGeneratorToolCount = 0

// Define simple data classes for our tool inputs (reusing from AgentToolsTest)
@Serializable
data class MultiStepCalculatorInput(val operation: String, val a: Int, val b: Int)

@Serializable
data class MultiStepWeatherInput(val query: String)

// Define a simple data class for our tool output
@Serializable
data class MultiStepCalculatorOutput(val result: Int)

@Serializable
data class RandomGeneratorInput(val name: String)

@Serializable
data class RandomGeneratorOutput(val randomNumber: Int)

val randomGeneratorTool = Tool(
  name = "RandomGenerator",
  description = "Generates a random number.",
) { input: RandomGeneratorInput ->
    randomGeneratorToolCount++
    RandomGeneratorOutput((0..100).random())
}

// Helper functions for the tools that track invocation count
fun multiStepCalculateResult(input: MultiStepCalculatorInput): MultiStepCalculatorOutput {
    calculatorInvocationCount++

    val result = when (input.operation) {
        "add" -> input.a + input.b
        "subtract" -> input.a - input.b
        "multiply" -> input.a * input.b
        "divide" -> input.a / input.b
        else -> throw IllegalArgumentException("Unknown operation: ${input.operation}")
    }

    return MultiStepCalculatorOutput(result)
}

fun multiStepGetWeather(input: MultiStepWeatherInput): String {
    weatherInvocationCount++
    return "The weather in ${input.query} is sunny and 75 degrees."
}

// Create tools for testing
val multiStepCalculatorTool = Tool(
  name = "Calculator",
  description = "A simple calculator that can add, subtract, multiply, and divide two numbers."
) { input: MultiStepCalculatorInput -> multiStepCalculateResult(input) }

val multiStepWeatherTool = Tool(
  name = "Weather",
  description = "Get the current weather for a location."
) { input: MultiStepWeatherInput -> multiStepGetWeather(input) }

class AgentToolRoundsTest {

    // Create an agent with tools for testing
    private val testAgentWithTools = Agent(
        name = "Test Agent With Tools",
        description = "A test agent with tools for unit tests",
        system = "You are a helpful assistant for testing purposes. Use the tools when appropriate.",
        model = Model.default,
        tools = listOf(multiStepCalculatorTool, multiStepWeatherTool),
        parameters = RequestParameters(maxSteps = RequestParameters.MAX_STEPS)
    )

    @Test
    fun `test agent with multiple tool rounds`() = workflowWithEmptyState {
        // Reset tracking variables
        calculatorInvocationCount = 0
        weatherInvocationCount = 0

        // Test with a prompt that should trigger multiple tool calls
        val response: String = testAgentWithTools(
            "First, calculate 5 plus 3. Then, tell me the weather in New York. Finally, multiply the result of the first calculation by 2."
        )

        // Verify the response
        assertNotNull(response)
        assertTrue(response.isNotEmpty())
        println("Response: $response")

        // Verify that both tools were invoked and the calculator was used twice
        assertTrue(calculatorInvocationCount > 0, "Calculator tool should have been invoked")
        assertTrue(weatherInvocationCount > 0, "Weather tool should have been invoked")

        // We expect at least 3 rounds: calculator, weather, calculator again
        val totalInvocations = calculatorInvocationCount + weatherInvocationCount
        assertTrue(totalInvocations >= 3, "Expected at least 3 tool invocations, but got $totalInvocations")

        println("Calculator invocations: $calculatorInvocationCount")
        println("Weather invocations: $weatherInvocationCount")
        println("Total invocations: $totalInvocations")
    }

    @Test
    fun `test agent with maxSteps limit`() = workflowWithEmptyState {
        randomGeneratorToolCount = 0

        // Create a new agent with a low maxSteps value to limit the number of rounds
        val agentWithLimitedSteps: Agent = Agent(
            name = "Test Agent With Limited Steps",
            description = "A test agent with tools for unit tests and limited steps",
            system = "You are a helpful assistant for testing purposes. Use the tools when appropriate.",
            model = Model.default,
            tools = listOf(randomGeneratorTool, +::calculateResult),
            parameters = RequestParameters(maxSteps = 10)
        )

        val result: Flow<StreamResponse<String>> = agentWithLimitedSteps.stream("call the random generator tool until you get the number `1000` back")

        result.collect {
            when (it) {
              is StreamResponse.Chunk<*> ->
                  print(it.value)
              StreamResponse.End ->
                  println("End of stream")
              is StreamResponse.Metadata ->
                  println("Metadata: ${it.value}")
              is StreamResponse.ToolCall ->
                  println("Tool call: ${it.value}")
              is StreamResponse.ToolResult ->
                  println("Tool result: ${it.value}")
            }
        }

        assertTrue(randomGeneratorToolCount <= 20,
            "Expected at most 20 tool invocations with maxSteps=10 (considering batch processing), but got $randomGeneratorToolCount")
    }
}
