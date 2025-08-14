package predictable

import kotlinx.coroutines.flow.toList
import predictable.TestUtils.workflowWithEmptyState
import predictable.agent.Message
import predictable.agent.Model
import predictable.agent.StreamResponse
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AgentTest {

  // Create a simple agent for testing
  private val testAgent = agent(
    name = "Test Agent",
    description = "A test agent for unit tests",
    system = "You are a helpful assistant for testing purposes. Keep your answers short and concise.",
    model = Model.default, // Using gpt-4o-mini as required
    tools = emptyList(),
  )

  @Test
  fun `test invoke with string input`() = workflowWithEmptyState {
    // Test the invoke method with a string input
    val response: String = testAgent("Hello, who are you?")
    // Verify the response
    assertNotNull(response)
    assertTrue(response.isNotEmpty())
    println("Response: $response")
  }

  @Test
  fun `test invoke with AgentInput String`() = workflowWithEmptyState {
    // Test the invoke method with an AgentInput.String
    val response = testAgent("What is 2+2?")
    assertTrue(response.isNotEmpty())
  }

  @Test
  fun `test invoke with AgentInput Messages`() = workflowWithEmptyState {

    // Create a list of messages
    val messages = listOf(
      Message.user("Hello, who are you?"),
      Message.assistant("I am a test assistant."),
      Message.user("What can you do?")
    )

    val response = testAgent(messages)

    assertTrue(response.isNotEmpty())
  }

  @Test
  fun `test stream with string input`() = workflowWithEmptyState {

    // Test the stream method with a string input
    val streamResponse = testAgent.stream("Hello, who are you?")

    // Collect all chunks from the stream
    val chunks = streamResponse.toList()

    // Verify that we received chunks
    assertTrue(chunks.isNotEmpty())
  }

  @Test
  fun `test stream with list of messages`() = workflowWithEmptyState {
    // Create a list of messages
    val messages = listOf(
      Message.user("What's the weather like today?"),
      Message.assistant("I don't have access to real-time weather information."),
      Message.user("Then tell me what you can do")
    )

    // Test the stream method with a list of messages
    val streamResponse = testAgent.stream(messages)

    // Collect all chunks from the stream
    val chunks = streamResponse.toList()

    // Verify that we received chunks
    assertTrue(chunks.isNotEmpty())

    // Print all chunks for debugging
    chunks.forEach { chunk ->
      when (chunk) {
        is StreamResponse.Chunk<*> -> println("Chunk: ${chunk.value}")
        is StreamResponse.Metadata -> println("Metadata: ${chunk.value}")
        StreamResponse.End -> println("End of stream")
        is StreamResponse.ToolCall -> println("ToolCall: ${chunk.value}")
        is StreamResponse.ToolResult -> println("ToolResult: ${chunk.value}")
      }
    }
  }

}
