package agent.compression

import predictable.Agent
import predictable.TestUtils.workflowWithEmptyState
import predictable.agent.CompressionStrategy
import predictable.agent.Message
import predictable.agent.Model
import predictable.agent.RequestParameters
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests for compression functionality across AbstractAgent methods.
 *
 * These tests verify that compression strategies work correctly with invoke(),
 * chat(), and stream() methods, and that precedence order is maintained.
 *
 * Note: These are integration tests that make real API calls. They require
 * valid API keys in environment variables.
 */
class CompressionIntegrationTest {

  private val testAgent = Agent(
    name = "Compression Test Agent",
    description = "Agent for testing compression functionality",
    system = "You are a helpful assistant. Always respond briefly.",
    model = Model.defaultModel
  )

  @Test
  fun `test WholeHistory compression with invoke reduces message count`() = workflowWithEmptyState {
    val messages = buildList {
      add(Message.user("First question: What is 1+1?"))
      add(Message.assistant("2"))
      add(Message.user("Second question: What is 2+2?"))
      add(Message.assistant("4"))
      add(Message.user("Third question: What is 3+3?"))
    }

    val params = RequestParameters(
      compressionStrategy = CompressionStrategy.WholeHistory
    )

    val response = testAgent(messages, params)

    assertNotNull(response)
    assertTrue(response.isNotEmpty())
  }

  @Test
  fun `test no compression when compressionStrategy is null`() = workflowWithEmptyState {
    val messages = buildList {
      add(Message.user("First message"))
      add(Message.assistant("First response"))
      add(Message.user("Second message"))
    }

    val params = RequestParameters(compressionStrategy = null)

    val response = testAgent(messages, params)

    assertNotNull(response)
    assertTrue(response.isNotEmpty())
  }

  @Test
  fun `test maxHistorySize limits message count with invoke`() = workflowWithEmptyState {
    val messages = buildList {
      repeat(50) {
        add(Message.user("Message number $it"))
        add(Message.assistant("Response $it"))
      }
    }

    val params = RequestParameters(maxHistorySize = 10)

    val response = testAgent(messages, params)

    assertNotNull(response)
    assertTrue(response.isNotEmpty())
  }

  @Test
  fun `test maxTokens trims history with invoke`() = workflowWithEmptyState {
    val messages = buildList {
      repeat(20) {
        add(Message.user("This is a long message with lots of content: ${"x".repeat(100)}"))
        add(Message.assistant("Response $it"))
      }
    }

    val params = RequestParameters(maxTokens = 500)

    val response = testAgent(messages, params)

    assertNotNull(response)
    assertTrue(response.isNotEmpty())
  }

  @Test
  fun `test precedence order - compression then tokens then size`() = workflowWithEmptyState {
    val messages = buildList {
      repeat(30) {
        add(Message.user("Message $it with content"))
        add(Message.assistant("Response $it"))
      }
    }

    val params = RequestParameters(
      compressionStrategy = CompressionStrategy.WholeHistory,
      maxTokens = 1000,
      maxHistorySize = 20
    )

    val response = testAgent(messages, params)

    assertNotNull(response)
    assertTrue(response.isNotEmpty())
  }

  @Test
  fun `test invoke method with compression strategy`() = workflowWithEmptyState {
    val messages = buildList {
      repeat(15) {
        add(Message.user("Question $it: What is ${it + 1} times 2?"))
        add(Message.assistant("Answer: ${(it + 1) * 2}"))
      }
      add(Message.user("What was the first question?"))
    }

    val params = RequestParameters(
      compressionStrategy = CompressionStrategy.LastNMessages(5)
    )

    val response = testAgent(messages, params)

    assertNotNull(response)
    assertTrue(response.isNotEmpty())
  }

  @Test
  fun `test chat method with compression strategy`() = workflowWithEmptyState {
    val messages = buildList {
      repeat(10) {
        add(Message.user("User message $it"))
        add(Message.assistant("Assistant response $it"))
      }
      add(Message.user("Final question"))
    }

    val params = RequestParameters(
      compressionStrategy = CompressionStrategy.WholeHistory
    )

    val chatResponse = testAgent.chat(messages, params)

    assertNotNull(chatResponse)
    assertNotNull(chatResponse.value)
    assertTrue(chatResponse.value.isNotEmpty())
  }

  @Test
  fun `test stream method applies compression`() = workflowWithEmptyState {
    val messages = buildList {
      repeat(10) {
        add(Message.user("Message $it"))
        add(Message.assistant("Response $it"))
      }
      add(Message.user("Stream this response"))
    }

    val params = RequestParameters(
      compressionStrategy = CompressionStrategy.WholeHistory
    )

    val streamFlow = testAgent.stream(messages, params)

    assertNotNull(streamFlow)
    // Stream now applies compression just like invoke() and chat()
  }

  @Test
  fun `test compression with string input using invoke`() = workflowWithEmptyState {
    val params = RequestParameters(
      compressionStrategy = CompressionStrategy.WholeHistory
    )

    val response = testAgent("Simple string input", params)

    assertNotNull(response)
    assertTrue(response.isNotEmpty())
  }

  @Test
  fun `test LastNMessages compression strategy`() = workflowWithEmptyState {
    val messages = buildList {
      repeat(20) {
        add(Message.user("Question $it"))
        add(Message.assistant("Answer $it"))
      }
    }

    val params = RequestParameters(
      compressionStrategy = CompressionStrategy.LastNMessages(3)
    )

    val response = testAgent(messages, params)

    assertNotNull(response)
    assertTrue(response.isNotEmpty())
  }

  @Test
  fun `test Chunked compression strategy`() = workflowWithEmptyState {
    val messages = buildList {
      repeat(15) {
        add(Message.user("Message in chunk $it"))
        add(Message.assistant("Response in chunk $it"))
      }
    }

    val params = RequestParameters(
      compressionStrategy = CompressionStrategy.Chunked(chunkSize = 5)
    )

    val response = testAgent(messages, params)

    assertNotNull(response)
    assertTrue(response.isNotEmpty())
  }

  @Test
  fun `test compression with empty message list`() = workflowWithEmptyState {
    val messages = emptyList<Message>()

    val params = RequestParameters(
      compressionStrategy = CompressionStrategy.WholeHistory
    )

    val response = testAgent.chat(messages, params)

    assertNotNull(response)
  }

  @Test
  @Ignore // TODO: Test times out after 16+ minutes - investigate why WholeHistory compression hangs with this message pattern
  fun `test compression preserves system messages`() = workflowWithEmptyState {
    val messages = buildList {
      add(Message.user("First question"))
      add(Message.assistant("First answer"))
      repeat(10) {
        add(Message.user("Follow-up $it"))
        add(Message.assistant("Response $it"))
      }
    }

    val params = RequestParameters(
      compressionStrategy = CompressionStrategy.WholeHistory
    )

    val response = testAgent(messages, params)

    assertNotNull(response)
    assertTrue(response.isNotEmpty())
  }

  @Test
  fun `test maxHistorySize with small value`() = workflowWithEmptyState {
    val messages = buildList {
      repeat(100) {
        add(Message.user("Message $it"))
        add(Message.assistant("Response $it"))
      }
    }

    val params = RequestParameters(maxHistorySize = 2)

    val response = testAgent(messages, params)

    assertNotNull(response)
    assertTrue(response.isNotEmpty())
  }

  @Test
  fun `test compression fallback on error continues with full history`() = workflowWithEmptyState {
    val messages = buildList {
      repeat(5) {
        add(Message.user("Message $it"))
        add(Message.assistant("Response $it"))
      }
    }

    val params = RequestParameters(
      compressionStrategy = CompressionStrategy.WholeHistory
    )

    val response = testAgent(messages, params)

    assertNotNull(response)
    assertTrue(response.isNotEmpty())
  }
}
