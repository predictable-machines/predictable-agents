package predictable

import kotlinx.serialization.Serializable
import predictable.TestUtils.workflowWithEmptyState
import predictable.agent.Model
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AgentToolCrashTest {

  @Serializable
  data class ToolInput(val query: String)

  @Serializable
  data class ToolOutput(val result: String)

  val tool = Tool<ToolInput, ToolOutput>(
    name = "TestTool",
  ) {
    throw IllegalStateException("Tool error")
  }

  // Create a simple agent for testing
  private val testAgent = Agent(
    name = "Test Agent",
    description = "A test agent for unit tests",
    system = "You are a helpful assistant for testing purposes",
    model = Model.defaultModel, // Using gpt-4o-mini as required
    tools = listOf(tool),
  )

  @Test
  fun `test agent does not crash when tool fails`() = workflowWithEmptyState {
    // Test the invoke method with a string input
    val response: String = testAgent("Call the test tool with input: 'Hello'")
    // Verify the response
    assertNotNull(response)
    assertTrue(response.isNotEmpty())
    println("Response: $response")
  }

}
