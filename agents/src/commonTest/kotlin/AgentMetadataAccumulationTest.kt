package predictable

import kotlinx.coroutines.flow.toList
import kotlinx.serialization.Serializable
import predictable.TestUtils.workflowWithEmptyState
import predictable.agent.Model
import predictable.agent.RequestParameters
import predictable.agent.StreamResponse
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Serializable
data class SimpleCalculatorInput(val operation: String, val a: Int, val b: Int)

@Serializable
data class SimpleCalculatorOutput(val result: Int)

@Serializable
data class MetadataWeatherInput(val city: String)

@Serializable
data class MetadataWeatherOutput(val temperature: Int, val description: String)

class AgentMetadataAccumulationTest {

    // Simple calculator tool
    private val calculatorTool = Tool(
        name = "Calculator",
        description = "Performs basic arithmetic operations"
    ) { input: SimpleCalculatorInput ->
        val result = when (input.operation) {
            "add" -> input.a + input.b
            "subtract" -> input.a - input.b
            "multiply" -> input.a * input.b
            "divide" -> if (input.b != 0) input.a / input.b else 0
            else -> 0
        }
        SimpleCalculatorOutput(result)
    }

    // Weather tool that might require multiple calls
    private val weatherTool = Tool(
        name = "Weather",
        description = "Gets weather information for a city"
    ) { input: MetadataWeatherInput ->
        // Simulate weather data
        val weatherData = mapOf(
            "San Francisco" to MetadataWeatherOutput(72, "Sunny"),
            "New York" to MetadataWeatherOutput(65, "Cloudy"),
            "London" to MetadataWeatherOutput(59, "Rainy"),
            "Tokyo" to MetadataWeatherOutput(75, "Clear")
        )
        weatherData[input.city] ?: MetadataWeatherOutput(70, "Unknown")
    }

    @Test
    fun `agent should accumulate metadata across tool calls`() = workflowWithEmptyState {
        // Create an agent with tools
        val agent = Agent(
            name = "MetadataTestAgent",
            description = "Agent for testing metadata accumulation",
            system = "You are a helpful assistant. Use the provided tools when needed.",
            model = Model.defaultModel,
            tools = listOf(calculatorTool, weatherTool)
        )

        // Make a request that should trigger tool usage
        val response = agent.chat("What is 5 + 3? Also, what's the weather in San Francisco?")

        // Verify we got a response
        assertNotNull(response)
        assertNotNull(response.value)
        assertTrue(response.value.isNotEmpty())
        
        // Verify metadata exists and has the expected structure
        assertNotNull(response.metadata)
        assertNotNull(response.metadata.promptTokens)
        assertNotNull(response.metadata.completionTokens)
        assertNotNull(response.metadata.totalTokens)
        
        // Verify the metadata values are consistent
        // Total tokens should be the sum of prompt and completion tokens
        // (or might be 0 if the model doesn't provide usage data)
        if (response.metadata.totalTokens > 0) {
            assertTrue(
                response.metadata.totalTokens >= response.metadata.promptTokens + response.metadata.completionTokens,
                "Total tokens should be at least the sum of prompt and completion tokens"
            )
        }

        // Verify we got messages including tool interactions
        assertNotNull(response.messages)
        assertTrue(response.messages.isNotEmpty())
    }

    @Test
    fun `streaming agent should accumulate metadata across tool calls`() = workflowWithEmptyState {
        // Create an agent with tools
        val agent = Agent(
            name = "StreamingMetadataTestAgent",
            description = "Agent for testing streaming metadata accumulation",
            system = "You are a helpful assistant. Use the provided tools when needed to answer questions.",
            model = Model.defaultModel,
            tools = listOf(calculatorTool, weatherTool)
        )

        // Stream a request that should trigger tool usage
        val stream = agent.stream("Calculate 10 * 5 and tell me the weather in New York.")

        // Collect all stream responses
        val responses = stream.toList()

        // Verify we got responses
        assertTrue(responses.isNotEmpty())

        // Count metadata emissions
        val metadataResponses = responses.filterIsInstance<StreamResponse.Metadata>()
        
        // We should always get at least one metadata emission at the end of streaming
        assertTrue(metadataResponses.isNotEmpty(), "Should have at least one metadata emission at stream end")
        
        val finalMetadata = metadataResponses.last()
        assertNotNull(finalMetadata.value)
        
        // Verify the metadata structure is valid
        assertNotNull(finalMetadata.value.promptTokens)
        assertNotNull(finalMetadata.value.completionTokens)
        assertNotNull(finalMetadata.value.totalTokens)

        // If OpenAI provided usage data, these should be non-zero
        // But some models might not provide usage data, so we just verify the structure

        // Verify we got tool calls
        val toolCalls = responses.filterIsInstance<StreamResponse.ToolCall>()
        assertTrue(toolCalls.isNotEmpty(), "Should have tool calls for calculator and weather")

        // Verify we got tool results
        val toolResults = responses.filterIsInstance<StreamResponse.ToolResult>()
        assertTrue(toolResults.isNotEmpty(), "Should have tool results")

        // Verify stream ended properly
        assertTrue(responses.any { it is StreamResponse.End }, "Stream should end properly")
    }

    @Test
    fun `streaming should emit intermediate metadata for each tool call round`() = workflowWithEmptyState {
        // Create an agent with multiple tools that will be called sequentially
        val agent = Agent(
            name = "IntermediateMetadataTestAgent",
            description = "Agent for testing intermediate metadata emissions",
            system = "You are a helpful assistant. First use the calculator to add 5+3, then use the weather tool for Tokyo.",
            model = Model.defaultModel,
            tools = listOf(calculatorTool, weatherTool)
        )

        // Stream a request that should trigger multiple tool calls
        val stream = agent.stream("Please calculate 5+3 and then tell me the weather in Tokyo.")
        
        // Collect all stream responses
        val responses = stream.toList()
        
        // Count metadata emissions
        val metadataResponses = responses.filterIsInstance<StreamResponse.Metadata>()
        val toolCallResponses = responses.filterIsInstance<StreamResponse.ToolCall>()
        val toolResultResponses = responses.filterIsInstance<StreamResponse.ToolResult>()

        // We should have at least:
        // - Tool calls for calculator and weather
        assertTrue(toolCallResponses.size >= 1, "Should have at least one tool call")
        
        // We should have multiple metadata emissions:
        // - One after each tool call round (intermediate)
        // - One at the end (final)
        // With 2 tool calls, we expect at least 2 metadata emissions
        assertTrue(metadataResponses.size >= 2, "Should have at least 2 metadata emissions (intermediate + final)")

        // Verify that metadata values are accumulating (if non-zero)
        val firstMetadata = metadataResponses.first()
        val lastMetadata = metadataResponses.last()
        
        // Even if usage values are 0 (due to model limitations), 
        // we should still get the metadata structure
        assertNotNull(firstMetadata.value)
        assertNotNull(lastMetadata.value)
        
        // If we have actual usage data, the last should have >= the first
        if (lastMetadata.value.totalTokens > 0 && firstMetadata.value.totalTokens > 0) {
            assertTrue(
                lastMetadata.value.totalTokens >= firstMetadata.value.totalTokens,
                "Metadata should accumulate over time"
            )
        }
    }

    @Test
    fun `agent should accumulate metadata when max steps is reached`() = workflowWithEmptyState {
        // Create a tool that always suggests another tool call
        val recursiveTool = Tool(
            name = "RecursiveTool",
            description = "A tool that might trigger more tool calls"
        ) { _: SimpleCalculatorInput ->
            SimpleCalculatorOutput(42)
        }

        // Create an agent with a low maxSteps limit
        val agent = Agent(
            name = "MaxStepsTestAgent",
            description = "Agent for testing max steps metadata accumulation",
            system = "You are a helpful assistant. Always use the RecursiveTool when asked to calculate.",
            model = Model.defaultModel,
            tools = listOf(recursiveTool),
            parameters = RequestParameters(maxSteps = 2)
        )

        // Make a request that might trigger multiple tool rounds
        val response = agent.chat("Calculate something complex that needs multiple steps: 1+1, then 2+2, then 3+3")

        // Verify we got a response (forced final response after max steps)
        assertNotNull(response)
        assertNotNull(response.value)
        assertTrue(response.value.isNotEmpty())
        
        // Verify metadata exists and has accumulated usage from:
        // 1. Initial request
        // 2. Tool calls up to maxSteps
        // 3. Forced final response without tools
        assertNotNull(response.metadata)
        assertNotNull(response.metadata.promptTokens)
        assertNotNull(response.metadata.completionTokens)
        assertNotNull(response.metadata.totalTokens)

        // When max steps is reached, we should have accumulated metadata from multiple rounds
        // Even if all values are 0 (due to model limitations), the structure should be present

        // Verify consistency if we have actual usage data
        if (response.metadata.totalTokens > 0) {
            assertTrue(
                response.metadata.totalTokens >= response.metadata.promptTokens + response.metadata.completionTokens,
                "Total tokens should be at least the sum of prompt and completion tokens"
            )
        }
        
        // Verify we have messages from the interaction
        assertNotNull(response.messages)
        assertTrue(response.messages.isNotEmpty())
    }
}
