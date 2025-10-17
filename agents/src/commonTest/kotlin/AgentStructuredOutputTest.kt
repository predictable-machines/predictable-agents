package predictable

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import predictable.TestUtils.workflowWithEmptyState
import predictable.agent.Model
import predictable.agent.StreamResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// Define simple data classes for structured output testing
@Serializable
@SerialName("WeatherRequest")
data class WeatherRequest(val location: String, val unit: String = "celsius")

@Serializable
@SerialName("WeatherResponse")
data class WeatherResponse(
    val location: String,
    val temperature: Double,
    val conditions: String,
)

@Serializable
@SerialName("PersonInfo")
data class PersonInfo(
    val name: String,
    val age: Int,
    val occupation: String
)

class AgentStructuredOutputTest {

    // Create a simple agent for testing structured output
    private val testAgent = Agent(
        name = "Structured Output Test Agent",
        description = "A test agent for structured output unit tests",
        system = "You are a helpful assistant for testing structured output. Always respond with the exact structure requested.",
        model = Model.defaultModel, // Using gpt-4o-mini as required
        tools = emptyList(),
    )

    @Test
    fun `test structured output with weather data`() = workflowWithEmptyState {

        // Test the structured output with a weather request
        val request = WeatherRequest(location = "London")
        val response: WeatherResponse = testAgent.generateObject(request)

        // Verify the response
        assertNotNull(response)
        assertEquals("London", response.location)
        assertTrue(response.conditions.isNotEmpty())
    }

    @Test
    fun `test structured output with person info`() = workflowWithEmptyState {

        // Test the structured output with a person description
        val description = "John Doe is a 35-year-old software engineer."
        val response: PersonInfo = testAgent.generateObject(description)

        // Verify the response
        assertNotNull(response)
        assertTrue(response.name.isNotEmpty())
        assertTrue(response.age > 0)
        assertTrue(response.occupation.isNotEmpty())
    }

    @Test
    fun `test streaming structured output with weather data`() = workflowWithEmptyState {
        val request = WeatherRequest(location = "New York", unit = "fahrenheit")
        // Create a structured output streamer for WeatherResponse
        val weatherStreamer: Flow<StreamResponse<WeatherResponse>> = testAgent.stream(request)

        // Test the streaming structured output with a weather request
        val streamResponses = weatherStreamer.toList()

        // Verify that we got some responses
        assertTrue(streamResponses.isNotEmpty(), "Stream responses should not be empty")

        // Verify that we got an end marker
        val endMarkers = streamResponses.filterIsInstance<StreamResponse.End>()
        assertTrue(endMarkers.isNotEmpty(), "Should have received an end marker")
    }

    @Test
    fun `test streaming structured output with person info`() = workflowWithEmptyState {
        // Create a structured output streamer for PersonInfo
        val personInfoStreamer = testAgent.streamObject<String, PersonInfo>()

        // Test the streaming structured output with a person description
        val description = "Jane Smith is a 42-year-old doctor."
        val streamResponses = personInfoStreamer(description).toList()

        // Verify that we got some responses
        assertTrue(streamResponses.isNotEmpty(), "Stream responses should not be empty")

        // Verify that we got an end marker
        val endMarkers = streamResponses.filterIsInstance<StreamResponse.End>()
        assertTrue(endMarkers.isNotEmpty(), "Should have received an end marker")
    }
}
