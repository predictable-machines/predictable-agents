# Data Model: Developer-Controlled History Management

**Feature**: Developer-Controlled History Management
**Branch**: `003-history-management-control`
**Date**: 2025-10-16
**Based on**: [research.md](./research.md), [spec.md](./spec.md)

## Overview

This document defines all data structures required to integrate Koog's compression, history management, and persistence features into our AbstractAgent through RequestParameters.

**Design Principle**: Pure delegation - all types map directly to Koog's native APIs with minimal wrapper logic.

## 1. Request Parameters Extensions

### File

`agents/src/commonMain/kotlin/predictable/agent/RequestParameters.kt`

### Current Structure

```kotlin
@Serializable
data class RequestParameters(
  val temperature: Double? = null,
  val maxCompletionTokens: Int? = null,
  val topP: Double? = null,
  val topK: Int? = null,
  val frequencyPenalty: Double? = null,
  val presencePenalty: Double? = null,
  val responseFormat: ResponseFormat? = null,
  val stop: List<String>? = null,
  val seed: Int? = null,
  val user: String? = null,
  val parallelToolCalls: Boolean? = null,
  val customHeaders: Map<String, String>? = null
)
```

### New Fields (to be added)

```kotlin
@Serializable
data class RequestParameters(
  // ... existing fields ...

  /**
   * Compression strategy to apply to conversation history before sending to model.
   *
   * When specified, history is compressed using Koog's replaceHistoryWithTLDR() which:
   * 1. Generates a TL;DR summary via LLM
   * 2. Preserves system messages and first user message
   * 3. Replaces remaining history with summary
   *
   * **Precedence**: Applied BEFORE maxTokens and maxHistorySize checks.
   * **Delegation**: Maps directly to ai.koog.agents.core.dsl.extension.HistoryCompressionStrategy
   *
   * @see ai.koog.agents.core.dsl.extension.HistoryCompressionStrategy
   * @see ai.koog.agents.core.dsl.extension.replaceHistoryWithTLDR
   */
  val compressionStrategy: HistoryCompressionStrategy? = null,

  /**
   * Maximum number of messages to retain in history.
   *
   * When history exceeds this limit, oldest messages are dropped from start of list.
   * System messages are always preserved regardless of limit.
   *
   * **Precedence**: Applied AFTER compressionStrategy and maxTokens.
   * **Delegation**: Uses Koog's Prompt.withMessages { messages.takeLast(n) }
   *
   * Example: maxHistorySize = 20 keeps most recent 20 messages
   *
   * @see ai.koog.prompt.dsl.Prompt.withMessages
   */
  val maxHistorySize: Int? = null,

  /**
   * Maximum estimated token count for entire prompt (including system, history, and new message).
   *
   * When token estimate exceeds limit:
   * 1. If compressionStrategy is set, compression is applied first
   * 2. If still over limit, messages are trimmed from start until under limit
   *
   * **Precedence**: Applied AFTER compressionStrategy, BEFORE maxHistorySize.
   * **Delegation**: Uses Koog's PromptTokenizer.tokenCountFor(prompt)
   * **Validation**: Must be >= system prompt token count (enforced via CompressionError.InvalidTokenLimit)
   *
   * Example: maxTokens = 50000 ensures prompt stays within 50k token budget
   *
   * @see ai.koog.prompt.tokenizer.PromptTokenizer
   * @see ai.koog.prompt.tokenizer.CachingTokenizer
   */
  val maxTokens: Int? = null,

  /**
   * Enable caching for token counts to improve performance.
   *
   * When true (default), uses CachingTokenizer which caches token counts per message.
   * When false, uses OnDemandTokenizer which recalculates every time.
   *
   * **Delegation**:
   * - true → ai.koog.prompt.tokenizer.CachingTokenizer
   * - false → ai.koog.prompt.tokenizer.OnDemandTokenizer
   *
   * Recommendation: Keep true unless debugging token count discrepancies
   *
   * @see ai.koog.prompt.tokenizer.CachingTokenizer
   * @see ai.koog.prompt.tokenizer.OnDemandTokenizer
   */
  val enableTokenCaching: Boolean = true
)
```

### Validation Rules

1. **maxTokens validation**:
   ```kotlin
   context(Raise<CompressionError>)
   fun validateMaxTokens(systemPromptTokens: Int, maxTokens: Int?) {
     maxTokens?.let {
       ensure(it >= systemPromptTokens) {
         CompressionError.InvalidTokenLimit(systemPromptTokens, it)
       }
     }
   }
   ```

2. **maxHistorySize validation**:
   ```kotlin
   context(Raise<CompressionError>)
   fun validateMaxHistorySize(maxHistorySize: Int?) {
     maxHistorySize?.let {
       ensure(it > 0) {
         CompressionError.InsufficientHistory(messageCount = 0, minimum = 1)
       }
     }
   }
   ```

3. **Compression strategy validation**:
   ```kotlin
   context(Raise<CompressionError>)
   fun validateCompressionStrategy(
     strategy: HistoryCompressionStrategy?,
     messageCount: Int
   ) {
     if (strategy != null) {
       // Minimum 10 messages required for meaningful compression
       ensure(messageCount >= 10) {
         CompressionError.InsufficientHistory(messageCount, minimum = 10)
       }
     }
   }
   ```

### Parameter Precedence Order

Applied in sequence (see FR-010 in spec.md):

```
1. compressionStrategy → Apply if specified
   ↓
2. maxTokens → Check and trim if over limit
   ↓
3. maxHistorySize → Apply message count limit
```

**Rationale**:
- Compression first preserves quality (intelligent summarization vs. truncation)
- Token limits prevent model errors
- Message limits are fallback for simple size control

## 2. Compression Error Types

### File

`agents/src/commonMain/kotlin/predictable/agent/compression/CompressionError.kt`

### Structure

```kotlin
package predictable.agent.compression

import arrow.core.raise.Raise

/**
 * Sealed class representing all possible errors during history compression operations.
 *
 * All compression functions use Arrow Raise context to propagate these typed errors:
 * ```kotlin
 * context(Raise<CompressionError>)
 * suspend fun compress(...)
 * ```
 */
sealed class CompressionError {

  /**
   * Error when maxTokens is set lower than system prompt token count.
   *
   * **Cause**: Developer specified maxTokens that cannot accommodate system prompt
   * **Recovery**: Increase maxTokens or remove system prompt content
   *
   * @property systemPromptTokens Token count of system prompt alone
   * @property maxTokens The invalid maxTokens value specified
   *
   * Example:
   * ```
   * System prompt: 5000 tokens
   * maxTokens: 3000 → InvalidTokenLimit(5000, 3000)
   * ```
   */
  data class InvalidTokenLimit(
    val systemPromptTokens: Int,
    val maxTokens: Int
  ) : CompressionError() {
    override fun toString(): String =
      "maxTokens ($maxTokens) must be >= system prompt tokens ($systemPromptTokens)"
  }

  /**
   * Error when compression operation fails.
   *
   * **Cause**: Koog's replaceHistoryWithTLDR() threw exception
   * **Common causes**:
   * - LLM API error during TL;DR generation
   * - Network failure
   * - Invalid message format
   *
   * @property strategy The compression strategy that failed (toString representation)
   * @property cause The underlying Kotlin exception
   *
   * Example:
   * ```
   * Koog throws: IOException("Connection timeout")
   * → CompressionFailed("WholeHistory", IOException(...))
   * ```
   */
  data class CompressionFailed(
    val strategy: String,
    val cause: Throwable
  ) : CompressionError() {
    override fun toString(): String =
      "Compression failed with strategy $strategy: ${cause.message}"
  }

  /**
   * Error when history has too few messages for compression.
   *
   * **Cause**: Compression requires minimum message count (typically 10)
   * **Recovery**: Wait for more conversation turns or disable compression
   *
   * @property messageCount Current number of messages in history
   * @property minimum Minimum required for this compression strategy
   *
   * Example:
   * ```
   * compressionStrategy = WholeHistory
   * messages = 3
   * → InsufficientHistory(messageCount = 3, minimum = 10)
   * ```
   */
  data class InsufficientHistory(
    val messageCount: Int,
    val minimum: Int
  ) : CompressionError() {
    override fun toString(): String =
      "Insufficient history: $messageCount messages (minimum $minimum required)"
  }
}
```

### Error Handling Pattern

```kotlin
import arrow.core.raise.Raise
import arrow.core.raise.ensure
import arrow.core.raise.withError

// Usage in compression adapter
context(Raise<CompressionError>)
suspend fun compressHistory(
  strategy: HistoryCompressionStrategy,
  messages: List<Message>
): List<Message> {
  // Validate minimum messages
  ensure(messages.size >= 10) {
    CompressionError.InsufficientHistory(messages.size, 10)
  }

  // Wrap Koog exceptions
  withError({ e: Throwable ->
    CompressionError.CompressionFailed(strategy.toString(), e)
  }) {
    koogLLMContext.writeSession {
      replaceHistoryWithTLDR(strategy, preserveMemory = true)
      prompt.messages.map { it.toPredictableMessage() }
    }
  }
}
```

## 3. History Compression Strategy (Delegated)

### File

**Source**: `ai.koog.agents.core.dsl.extension.HistoryCompressionStrategy`
**No wrapper needed** - use Koog's types directly in RequestParameters

### Structure (from Koog)

```kotlin
// From: /tmp/koog/agents/agents-core/src/commonMain/kotlin/ai/koog/agents/core/dsl/extension/HistoryCompressionStrategies.kt

sealed class HistoryCompressionStrategy {

  /**
   * Compress entire history to single TL;DR summary.
   * Preserves: system messages, first user message, memory messages
   * Compresses: all other messages
   */
  object WholeHistory : HistoryCompressionStrategy()

  /**
   * Keep last N messages, compress rest.
   *
   * @param n Number of most recent messages to preserve
   *
   * Example: FromLastNMessages(10) keeps last 10 messages, compresses older ones
   */
  data class FromLastNMessages(val n: Int) : HistoryCompressionStrategy()

  /**
   * Compress history in fixed-size chunks.
   *
   * @param chunkSize Number of messages per chunk
   *
   * Example: Chunked(5) creates TL;DR for every 5 messages
   */
  data class Chunked(val chunkSize: Int) : HistoryCompressionStrategy()

  /**
   * Compress messages before a timestamp, keep recent ones.
   *
   * @param timestamp Cutoff timestamp (kotlinx.datetime.Instant)
   *
   * Example: FromTimestamp(1HourAgo) compresses messages older than 1 hour
   */
  data class FromTimestamp(val timestamp: kotlinx.datetime.Instant) : HistoryCompressionStrategy()

  /**
   * Compress history with multiple system messages.
   * Splits by system message boundaries, compresses each section.
   * Useful when system prompt changes mid-conversation.
   */
  object WholeHistoryMultipleSystemMessages : HistoryCompressionStrategy()

  // Implementation in Koog
  abstract suspend fun compress(
    llmSession: AIAgentLLMWriteSession,
    memoryMessages: List<Message>
  )
}
```

### Usage in RequestParameters

```kotlin
// Example 1: Basic compression
RequestParameters(
  compressionStrategy = HistoryCompressionStrategy.WholeHistory
)

// Example 2: Keep last 10 messages
RequestParameters(
  compressionStrategy = HistoryCompressionStrategy.FromLastNMessages(10)
)

// Example 3: Compress in chunks
RequestParameters(
  compressionStrategy = HistoryCompressionStrategy.Chunked(5)
)

// Example 4: Compress before timestamp
RequestParameters(
  compressionStrategy = HistoryCompressionStrategy.FromTimestamp(
    Clock.System.now() - 1.hours
  )
)
```

## 4. Koog Agent Wrapper

### File

`agents/src/commonMain/kotlin/predictable/agent/KoogAgentWrapper.kt`

### Structure

```kotlin
package predictable.agent

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.context.AIAgentLLMContext
import ai.koog.utils.io.Closeable

/**
 * Internal wrapper for Koog's AIAgent providing lifecycle management.
 *
 * **Lifecycle**:
 * - Lazy initialization via AbstractAgent's lazy delegate
 * - Closed when AbstractAgent is closed
 * - Thread-safe via Koog's RWLock in AIAgentLLMContext
 *
 * **Purpose**:
 * - Provides access to AIAgentLLMContext for writeSession
 * - Maintains reference to original AbstractAgent config
 * - Handles cleanup on agent disposal
 *
 * @property koogAgent The wrapped Koog AIAgent instance
 * @property config Original AbstractAgent configuration
 * @property llmContext Lazy accessor for LLM context (from Running state)
 */
internal class KoogAgentWrapper(
  val koogAgent: AIAgent<String, String>,
  val config: AbstractAgent.Config
) : Closeable {

  /**
   * Access to Koog's LLM context for compression operations.
   *
   * **Requires**: Agent must be in Running state
   * **Provides**: Thread-safe writeSession {} and readSession {} access
   *
   * @throws IllegalStateException if agent not running
   */
  suspend fun getLLMContext(): AIAgentLLMContext {
    val state = koogAgent.getState()
    return when (state) {
      is AIAgent.Companion.State.Running -> state.rootContext.llm
      else -> error("Agent must be in Running state to access LLM context, current state: $state")
    }
  }

  /**
   * Check if agent is currently running.
   */
  suspend fun isRunning(): Boolean =
    koogAgent.getState() is AIAgent.Companion.State.Running

  /**
   * Close the wrapped Koog agent and release resources.
   */
  override fun close() {
    koogAgent.close()
  }
}

/**
 * AbstractAgent configuration needed for Koog agent creation.
 *
 * @property name Agent identifier
 * @property description Agent description
 * @property system System prompt content
 * @property model Model configuration
 * @property tools Available tool descriptors
 * @property apiKey API key for model provider
 */
internal data class Config(
  val name: String,
  val description: String,
  val system: String,
  val model: Model,
  val tools: List<Tool<*>>,
  val apiKey: String?
)
```

### Usage in AbstractAgent

```kotlin
abstract class AbstractAgent(...) {

  private val koogWrapper: KoogAgentWrapper by lazy {
    KoogAgentFactory.create(
      config = Config(name, description, system, model, tools, apiKey)
    )
  }

  suspend fun invoke(input: String, parameters: RequestParameters): String {
    // Apply history management if specified
    if (parameters.requiresHistoryManagement()) {
      applyHistoryManagement(parameters)
    }

    // Execute through Koog agent
    return koogWrapper.koogAgent.run(input)
  }

  override fun close() {
    koogWrapper.close()
  }
}
```

## 5. Message Converters

### File

`agents/src/commonMain/kotlin/predictable/agent/providers/MessageConverter.kt`

### Structure

```kotlin
package predictable.agent.providers

import ai.koog.prompt.message.Message as KoogMessage
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import kotlinx.datetime.Clock
import predictable.agent.Message as PredictableMessage
import predictable.agent.MessageRole
import predictable.tool.ToolCallRequest

/**
 * Bidirectional conversion between our Message type and Koog's Message type.
 *
 * **Key Differences**:
 * - Koog: Separate Tool.Call and Tool.Result types
 * - Us: toolCalls list on Assistant messages
 * - Koog: metaInfo with timestamps and token counts
 * - Us: No metadata (stateless)
 *
 * **Metadata Handling**:
 * - Our → Koog: Generate new timestamps via Clock.System.now()
 * - Koog → Our: Discard metadata (not part of our model)
 */
object MessageConverter {

  /**
   * Convert our Message to Koog's Message.
   *
   * @param clock Clock for generating timestamps
   * @return Koog Message (may be list of messages for tool calls)
   */
  fun PredictableMessage.toKoogMessage(clock: Clock): KoogMessage =
    when (role) {
      MessageRole.System -> KoogMessage.System(
        content = content,
        metaInfo = RequestMetaInfo.create(clock)
      )

      MessageRole.User -> KoogMessage.User(
        content = content,
        metaInfo = RequestMetaInfo.create(clock),
        attachments = emptyList()
      )

      MessageRole.Assistant -> {
        // If no tool calls, simple assistant message
        if (toolCalls.isNullOrEmpty()) {
          KoogMessage.Assistant(
            content = content,
            metaInfo = ResponseMetaInfo.create(clock),
            attachments = emptyList(),
            finishReason = null
          )
        } else {
          // Tool calls become separate Tool.Call messages
          // Note: This requires returning List<KoogMessage>
          // For now, return assistant message with tool info in content
          KoogMessage.Assistant(
            content = content.ifEmpty { "Calling tools: ${toolCalls.joinToString { it.function.name }}" },
            metaInfo = ResponseMetaInfo.create(clock),
            attachments = emptyList(),
            finishReason = null
          )
        }
      }

      is MessageRole.Custom -> {
        // Map custom roles to System with role in content
        KoogMessage.System(
          content = "[${role.name}] $content",
          metaInfo = RequestMetaInfo.create(clock)
        )
      }
    }

  /**
   * Convert list of our Messages to Koog Messages.
   * Handles tool call expansion.
   */
  fun List<PredictableMessage>.toKoogMessages(clock: Clock): List<KoogMessage> =
    flatMap { message ->
      when {
        message.role == MessageRole.Assistant && !message.toolCalls.isNullOrEmpty() -> {
          // Create Tool.Call messages
          message.toolCalls.map { toolCall ->
            KoogMessage.Tool.Call(
              id = toolCall.id,
              tool = toolCall.function.name,
              content = toolCall.function.arguments,
              metaInfo = ResponseMetaInfo.create(clock)
            )
          }
        }
        message.toolCallId != null -> {
          // Tool result message
          listOf(
            KoogMessage.Tool.Result(
              id = message.toolCallId,
              tool = message.name ?: "unknown",
              content = message.content,
              metaInfo = RequestMetaInfo.create(clock)
            )
          )
        }
        else -> listOf(message.toKoogMessage(clock))
      }
    }

  /**
   * Convert Koog's Message to our Message.
   * Discards metadata (timestamps, token counts).
   */
  fun KoogMessage.toPredictableMessage(): PredictableMessage =
    when (this) {
      is KoogMessage.System -> PredictableMessage.system(content)
      is KoogMessage.User -> PredictableMessage.user(content)
      is KoogMessage.Assistant -> PredictableMessage.assistant(content)

      is KoogMessage.Tool.Call -> PredictableMessage(
        role = MessageRole.Assistant,
        content = "",
        toolCalls = listOf(
          ToolCallRequest(
            id = id,
            type = tool,
            function = ToolCallRequest.Function(tool, content)
          )
        )
      )

      is KoogMessage.Tool.Result -> PredictableMessage(
        role = MessageRole.User,
        content = content,
        name = tool,
        toolCallId = id
      )
    }

  /**
   * Convert list of Koog Messages to our Messages.
   * Groups Tool.Call messages back into single Assistant message.
   */
  fun List<KoogMessage>.toPredictableMessages(): List<PredictableMessage> {
    val result = mutableListOf<PredictableMessage>()
    val pendingToolCalls = mutableListOf<ToolCallRequest>()

    for (message in this) {
      when (message) {
        is KoogMessage.Tool.Call -> {
          // Accumulate tool calls
          pendingToolCalls.add(
            ToolCallRequest(
              id = message.id,
              type = message.tool,
              function = ToolCallRequest.Function(message.tool, message.content)
            )
          )
        }
        else -> {
          // Flush pending tool calls if any
          if (pendingToolCalls.isNotEmpty()) {
            result.add(
              PredictableMessage(
                role = MessageRole.Assistant,
                content = "",
                toolCalls = pendingToolCalls.toList()
              )
            )
            pendingToolCalls.clear()
          }

          // Add current message
          result.add(message.toPredictableMessage())
        }
      }
    }

    // Flush any remaining tool calls
    if (pendingToolCalls.isNotEmpty()) {
      result.add(
        PredictableMessage(
          role = MessageRole.Assistant,
          content = "",
          toolCalls = pendingToolCalls.toList()
        )
      )
    }

    return result
  }
}
```

## 6. Validation Utilities

### File

`agents/src/commonMain/kotlin/predictable/agent/compression/HistoryValidation.kt`

### Structure

```kotlin
package predictable.agent.compression

import ai.koog.agents.core.dsl.extension.HistoryCompressionStrategy
import arrow.core.raise.Raise
import arrow.core.raise.ensure
import predictable.agent.Message

/**
 * Validation utilities for history management parameters.
 * All functions use Arrow Raise context for typed error handling.
 */
object HistoryValidation {

  /**
   * Validate compression strategy can be applied to current history.
   *
   * @param strategy Compression strategy (null = no compression)
   * @param messages Current message history
   * @raises CompressionError.InsufficientHistory if too few messages
   */
  context(Raise<CompressionError>)
  fun validateCompression(
    strategy: HistoryCompressionStrategy?,
    messages: List<Message>
  ) {
    if (strategy != null) {
      ensure(messages.size >= MIN_MESSAGES_FOR_COMPRESSION) {
        CompressionError.InsufficientHistory(
          messageCount = messages.size,
          minimum = MIN_MESSAGES_FOR_COMPRESSION
        )
      }
    }
  }

  /**
   * Validate maxTokens can accommodate system prompt.
   *
   * @param systemPromptTokens Token count of system prompt
   * @param maxTokens Maximum token limit
   * @raises CompressionError.InvalidTokenLimit if maxTokens < system prompt
   */
  context(Raise<CompressionError>)
  fun validateTokenLimit(systemPromptTokens: Int, maxTokens: Int?) {
    maxTokens?.let {
      ensure(it >= systemPromptTokens) {
        CompressionError.InvalidTokenLimit(systemPromptTokens, it)
      }
    }
  }

  /**
   * Validate maxHistorySize is positive.
   *
   * @param maxHistorySize Maximum message count
   * @raises CompressionError.InsufficientHistory if maxHistorySize <= 0
   */
  context(Raise<CompressionError>)
  fun validateHistorySize(maxHistorySize: Int?) {
    maxHistorySize?.let {
      ensure(it > 0) {
        CompressionError.InsufficientHistory(
          messageCount = 0,
          minimum = 1
        )
      }
    }
  }

  private const val MIN_MESSAGES_FOR_COMPRESSION = 10
}
```

## 7. Relationships and Dependencies

```
RequestParameters
    ↓ (contains)
HistoryCompressionStrategy (from Koog)
    ↓ (used by)
KoogCompressionAdapter
    ↓ (delegates to)
AIAgentLLMWriteSession.replaceHistoryWithTLDR()
    ↓ (modifies)
AIAgentLLMContext.prompt
    ↓ (contains)
List<ai.koog.prompt.message.Message>
    ↓ (converted via)
MessageConverter
    ↓ (produces)
List<predictable.agent.Message>
```

## 8. Type Safety Guarantees

1. **Sealed Error Types**: All compression errors are `CompressionError` subtypes
2. **Arrow Raise Context**: Type-safe error propagation without exceptions
3. **Immutable Data Classes**: All types are immutable `data class` or `sealed class`
4. **Delegation to Koog**: No reimplementation of compression logic
5. **Message Conversion**: Bidirectional conversion preserves message structure

## 9. Summary

| Entity | Purpose | Delegation |
|--------|---------|------------|
| `RequestParameters` | API surface for developers | Maps to Koog config |
| `CompressionError` | Typed error handling | Wraps Koog exceptions |
| `HistoryCompressionStrategy` | Compression algorithms | Direct Koog type |
| `KoogAgentWrapper` | Lifecycle management | Wraps `AIAgent` |
| `MessageConverter` | Type mapping | Converts between our/Koog types |
| `HistoryValidation` | Input validation | Uses Arrow Raise |

**Next Steps**:
1. Create `contracts/` directory with adapter interfaces
2. Create `quickstart.md` with usage examples
3. Run `/speckit.tasks` to generate implementation tasks
