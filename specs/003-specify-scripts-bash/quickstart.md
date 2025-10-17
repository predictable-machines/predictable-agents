# Quickstart: Developer-Controlled History Management

**Feature**: Developer-Controlled History Management
**Branch**: `003-history-management-control`
**Date**: 2025-10-16
**For**: Kotlin Multiplatform (JVM, JS, Native)

## Overview

This guide shows how to use conversation history compression and management features in your agents through `RequestParameters`. All features delegate to Koog's native implementations.

**Key Principle**: Explicit control - compression only happens when you specify it.

## Table of Contents

1. [Basic Compression](#1-basic-compression)
2. [Compression Strategies](#2-compression-strategies)
3. [Token-Based Limiting](#3-token-based-limiting)
4. [Message-Based Limiting](#4-message-based-limiting)
5. [Combined Strategies](#5-combined-strategies)
6. [Error Handling](#6-error-handling)
7. [Performance Considerations](#7-performance-considerations)
8. [Testing](#8-testing)

## 1. Basic Compression

### Compress Entire History

Use when conversation has grown long and you want to summarize everything:

```kotlin
import predictable.Agent
import predictable.agent.CompressionStrategy
import predictable.agent.Model
import predictable.agent.RequestParameters

// Create agent
val agent = Agent(
  name = "assistant",
  model = Model.CLAUDE_3_5_SONNET,
  system = "You are a helpful assistant"
)

// Have a long conversation...
agent.invoke("Tell me about Kotlin")
agent.invoke("What are coroutines?")
agent.invoke("How do I use Flow?")
// ... 50+ messages ...

// Compress entire history to TL;DR
val response = agent.invoke(
  input = "Continue our conversation",
  requestParameters = RequestParameters(
    compressionStrategy = CompressionStrategy.WholeHistory
  )
)
```

**What happens**:
1. Agent sends current history to LLM with prompt: "Summarize conversation in TL;DR"
2. LLM generates summary
3. Original messages replaced with TL;DR
4. System message and first user message preserved
5. New input added after TL;DR
6. Agent continues with compressed context

**Use Case**: Long customer support conversations, extended research sessions

## 2. Compression Strategies

### Keep Last N Messages

Preserve recent context, compress older messages:

```kotlin
val response = agent.invoke(
  input = "What did we discuss earlier?",
  requestParameters = RequestParameters(
    compressionStrategy = CompressionStrategy.LastNMessages(10)
  )
)
```

**Result**: Last 10 messages kept intact, older ones become TL;DR

**Use Case**: Conversations where recent context matters (debugging, follow-up questions)

### Compress in Chunks

Break history into segments and summarize each:

```kotlin
val response = agent.invoke(
  input = "Summarize our entire discussion",
  requestParameters = RequestParameters(
    compressionStrategy = CompressionStrategy.Chunked(chunkSize = 5)
  )
)
```

**Result**: Every 5 messages become one TL;DR (10 TL;DRs for 50 messages)

**Use Case**: Multi-topic conversations (meeting notes, brainstorming sessions)

### Compress by Timestamp

Keep recent messages, compress older ones:

```kotlin
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.hours

val oneHourAgo = Clock.System.now() - 1.hours

val response = agent.invoke(
  input = "What's new?",
  requestParameters = RequestParameters(
    compressionStrategy = CompressionStrategy.FromTimestamp(oneHourAgo)
  )
)
```

**Result**: Messages older than 1 hour compressed, recent ones kept

**Use Case**: Long-running sessions with time-based context (daily standup, ongoing support)

### Multiple System Messages

For conversations where system prompt changes:

```kotlin
val response = agent.invoke(
  input = "Continue",
  requestParameters = RequestParameters(
    compressionStrategy = CompressionStrategy.WholeHistoryMultipleSystemMessages
  )
)
```

**Result**: Each section (between system messages) compressed separately

**Use Case**: Multi-phase conversations (research → analysis → recommendations)

## 3. Token-Based Limiting

### Prevent Context Window Overflow

Set maximum token budget:

```kotlin
val response = agent.invoke(
  input = "Long input requiring large context...",
  requestParameters = RequestParameters(
    maxTokens = 50000  // Claude 3.5 supports 200k, but budget 50k
  )
)
```

**What happens**:
1. Agent estimates total tokens (system + history + new input)
2. If over limit, trims oldest messages until under limit
3. System messages always preserved
4. Request proceeds with trimmed history

**Use Case**: Cost control, predictable latency, staying within model limits

### Combine with Compression

Use compression first for better quality:

```kotlin
val response = agent.invoke(
  input = "Continue our conversation",
  requestParameters = RequestParameters(
    compressionStrategy = CompressionStrategy.WholeHistory,
    maxTokens = 50000
  )
)
```

**Precedence**:
1. Compression applied (intelligent summarization)
2. Token count checked
3. If still over limit, trim messages

**Benefit**: Compression preserves more information than simple truncation

### Enable/Disable Token Caching

Control token counting performance:

```kotlin
// With caching (default, faster for repeated counts)
val response = agent.invoke(
  input = "...",
  requestParameters = RequestParameters(
    maxTokens = 50000,
    enableTokenCaching = true  // Default
  )
)

// Without caching (for debugging token discrepancies)
val debugResponse = agent.invoke(
  input = "...",
  requestParameters = RequestParameters(
    maxTokens = 50000,
    enableTokenCaching = false
  )
)
```

**Performance Impact**: Caching speeds up repeated token counts by ~10x

## 4. Message-Based Limiting

### Simple Message Count Limit

Keep last N messages, discard older ones:

```kotlin
val response = agent.invoke(
  input = "What did we just discuss?",
  requestParameters = RequestParameters(
    maxHistorySize = 20  // Keep last 20 messages
  )
)
```

**What happens**:
1. Agent counts messages
2. If over limit, keeps last 20 messages
3. System messages always preserved
4. No LLM call required (fast)

**Use Case**: Simple history management, mobile apps, low-latency requirements

### Precedence with Other Parameters

Message limit applied last:

```kotlin
val response = agent.invoke(
  input = "Continue",
  requestParameters = RequestParameters(
    compressionStrategy = CompressionStrategy.WholeHistory,  // 1st
    maxTokens = 50000,                                               // 2nd
    maxHistorySize = 20                                              // 3rd
  )
)
```

**Order of operations**:
1. Compress history (50 messages → 5 TL;DR messages)
2. Check tokens (if over 50k, trim to fit)
3. Check message count (if over 20, keep last 20)

## 5. Combined Strategies

### Cost-Optimized Long Conversations

Balance quality and cost:

```kotlin
val response = agent.invoke(
  input = "Continue our research discussion",
  requestParameters = RequestParameters(
    compressionStrategy = CompressionStrategy.LastNMessages(15),
    maxTokens = 80000,
    maxHistorySize = 25
  )
)
```

**Strategy**:
- Keep last 15 messages uncompressed (recent context)
- Compress older messages
- Ensure total stays under 80k tokens
- Hard limit of 25 messages total

**Use Case**: Production applications balancing quality, cost, and performance

### Mobile-Optimized

Minimize latency and bandwidth:

```kotlin
val response = agent.invoke(
  input = "Quick question",
  requestParameters = RequestParameters(
    maxHistorySize = 10  // Only send last 10 messages
    // No compression = no extra LLM call
    // No token counting = faster
  )
)
```

**Benefits**:
- Fast (no compression LLM call)
- Low bandwidth (fewer messages)
- Predictable behavior

**Use Case**: Mobile apps, real-time chat, embedded devices

### Research Sessions

Preserve all context with compression:

```kotlin
val response = agent.invoke(
  input = "Based on everything we've discussed, what's your recommendation?",
  requestParameters = RequestParameters(
    compressionStrategy = CompressionStrategy.Chunked(10),
    maxTokens = 180000  // Use most of Claude's 200k window
  )
)
```

**Strategy**:
- Compress in chunks (preserves topic structure)
- Use large token budget (keep compressed summaries)
- No message limit (keep all compressed chunks)

**Use Case**: Deep research, analysis, strategic planning

## 6. Error Handling

### With Arrow Raise

All compression operations use Arrow Raise context:

```kotlin
import arrow.core.raise.Raise
import arrow.core.raise.either
import arrow.core.raise.recover
import predictable.agent.compression.CompressionError

// Either-based error handling
val result = either {
  agent.invoke(
    input = "Continue",
    requestParameters = RequestParameters(
      compressionStrategy = CompressionStrategy.WholeHistory
    )
  )
}

result.fold(
  ifLeft = { error: CompressionError ->
    when (error) {
      is CompressionError.InvalidTokenLimit ->
        println("maxTokens (${ error.maxTokens}) must be >= system prompt tokens (${error.systemPromptTokens})")

      is CompressionError.CompressionFailed ->
        println("Compression failed with strategy ${error.strategy}: ${error.cause.message}")

      is CompressionError.InsufficientHistory ->
        println("Need ${error.minimum} messages for compression, only have ${error.messageCount}")
    }
  },
  ifRight = { response: String ->
    println("Success: $response")
  }
)
```

### Recover from Errors

Provide fallback strategies:

```kotlin
import arrow.core.raise.recover

val response = recover({
  agent.invoke(
    input = "Continue",
    requestParameters = RequestParameters(
      compressionStrategy = CompressionStrategy.WholeHistory,
      maxTokens = 50000
    )
  )
}) { error: CompressionError ->
  when (error) {
    is CompressionError.InsufficientHistory -> {
      // Not enough messages for compression, proceed without it
      agent.invoke(
        input = "Continue",
        requestParameters = RequestParameters(
          maxTokens = 50000  // Just use token limit
        )
      )
    }

    is CompressionError.CompressionFailed -> {
      // Compression LLM call failed, fall back to truncation
      agent.invoke(
        input = "Continue",
        requestParameters = RequestParameters(
          maxHistorySize = 20  // Simple message limit
        )
      )
    }

    is CompressionError.InvalidTokenLimit -> {
      // maxTokens too low, increase it
      agent.invoke(
        input = "Continue",
        requestParameters = RequestParameters(
          maxTokens = error.systemPromptTokens + 10000
        )
      )
    }
  }
}
```

### Validation Before Compression

Check parameters before API call:

```kotlin
import arrow.core.raise.ensure
import predictable.agent.compression.HistoryValidation

context(Raise<CompressionError>)
fun validateParameters(
  messages: List<Message>,
  parameters: RequestParameters
) {
  // Validate compression strategy
  HistoryValidation.validateCompression(
    parameters.compressionStrategy,
    messages
  )

  // Validate token limit
  HistoryValidation.validateTokenLimit(
    systemPromptTokens = 5000,  // Estimate or calculate
    maxTokens = parameters.maxTokens
  )

  // Validate history size
  HistoryValidation.validateHistorySize(
    parameters.maxHistorySize
  )
}

// Use before invoke
either {
  validateParameters(currentMessages, requestParams)
  agent.invoke(input, requestParams)
}
```

## 7. Performance Considerations

### Token Caching Impact

```kotlin
import kotlin.system.measureTimeMillis

// With caching (default)
val cachedTime = measureTimeMillis {
  repeat(100) {
    agent.invoke(
      input = "Test",
      requestParameters = RequestParameters(
        maxTokens = 50000,
        enableTokenCaching = true  // ~10ms per call
      )
    )
  }
}

// Without caching
val uncachedTime = measureTimeMillis {
  repeat(100) {
    agent.invoke(
      input = "Test",
      requestParameters = RequestParameters(
        maxTokens = 50000,
        enableTokenCaching = false  // ~100ms per call
      )
    )
  }
}

println("Cached: ${cachedTime}ms, Uncached: ${uncachedTime}ms")
// Cached: ~1000ms, Uncached: ~10000ms
```

**Recommendation**: Keep caching enabled unless debugging

### Compression Overhead

Compression requires LLM call:

```kotlin
// No compression = fast
val fast = agent.invoke(
  input = "Continue",
  requestParameters = RequestParameters(
    maxHistorySize = 20  // ~50ms overhead
  )
)

// With compression = slower (extra LLM call)
val slower = agent.invoke(
  input = "Continue",
  requestParameters = RequestParameters(
    compressionStrategy = CompressionStrategy.WholeHistory  // ~2-5s overhead
  )
)
```

**Trade-off**:
- Compression: Higher latency, better quality
- Truncation: Lower latency, simpler logic

### Async Compression

Pre-compress during idle time:

```kotlin
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

suspend fun preCompressInBackground() = coroutineScope {
  val compressionJob = async {
    agent.invoke(
      input = "",  // Empty input triggers compression only
      requestParameters = RequestParameters(
        compressionStrategy = CompressionStrategy.WholeHistory
      )
    )
  }

  // Do other work...
  userActivity()

  // Wait for compression if needed
  compressionJob.await()
}
```

**Use Case**: Mobile apps during screen transitions, background tasks

## 8. Testing

### Property-Based Testing

Verify compression properties:

```kotlin
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll

@Test
fun `compression reduces message count`() = runTest {
  checkAll(
    Arb.list(Arb.string(), range = 10..100)  // 10-100 messages
  ) { messages ->
    val compressed = compressHistory(
      CompressionStrategy.WholeHistory,
      messages
    )

    // Property: Compressed history has fewer messages
    assertTrue(compressed.size < messages.size)

    // Property: System messages preserved
    val systemCount = messages.count { it.role == MessageRole.System }
    assertEquals(
      systemCount,
      compressed.count { it.role == MessageRole.System }
    )
  }
}
```

### Integration Testing

Test with real agent:

```kotlin
@Test
fun `compression with token limit works end-to-end`() = runTest {
  val agent = Agent(
    name = "test-agent",
    model = Model.CLAUDE_3_5_SONNET,
    system = "You are a test assistant"
  )

  // Build long conversation
  repeat(50) {
    agent.invoke("Message $it")
  }

  // Apply compression + token limit
  val response = agent.invoke(
    input = "Summarize our discussion",
    requestParameters = RequestParameters(
      compressionStrategy = CompressionStrategy.WholeHistory,
      maxTokens = 10000
    )
  )

  // Verify success
  assertNotNull(response)
  assertTrue(response.isNotEmpty())

  // Verify history was actually compressed
  val historySize = agent.getHistory().size
  assertTrue(historySize < 50)
}
```

### Error Case Testing

Test error handling:

```kotlin
@Test
fun `insufficient history raises error`() = runTest {
  val result = either {
    compressHistory(
      strategy = CompressionStrategy.WholeHistory,
      messages = listOf(
        Message.system("System"),
        Message.user("User")
      )  // Only 2 messages, need 10
    )
  }

  result.fold(
    ifLeft = { error ->
      assertTrue(error is CompressionError.InsufficientHistory)
      assertEquals(2, (error as CompressionError.InsufficientHistory).messageCount)
      assertEquals(10, error.minimum)
    },
    ifRight = {
      fail("Should have raised InsufficientHistory error")
    }
  )
}
```

## Summary

### Quick Reference

| Goal | RequestParameters |
|------|-------------------|
| Compress all history | `compressionStrategy = WholeHistory` |
| Keep last N uncompressed | `compressionStrategy = FromLastNMessages(N)` |
| Stay under token budget | `maxTokens = N` |
| Simple message limit | `maxHistorySize = N` |
| Best quality + budget | `compressionStrategy + maxTokens` |
| Fastest (no compression) | `maxHistorySize` only |
| Mobile-optimized | `maxHistorySize = 10` |
| Research-optimized | `compressionStrategy = Chunked(10), maxTokens = 180000` |

### Precedence Order

Always applied in this order:

1. **compressionStrategy** - Intelligent summarization
2. **maxTokens** - Token budget enforcement
3. **maxHistorySize** - Simple message count limit

### Error Types

- `InvalidTokenLimit`: maxTokens < system prompt tokens
- `CompressionFailed`: LLM call failed during compression
- `InsufficientHistory`: Not enough messages for compression

### Best Practices

1. **Start simple**: Use `maxHistorySize` for basic limiting
2. **Add compression**: Use `compressionStrategy` for quality
3. **Control costs**: Add `maxTokens` for budget
4. **Handle errors**: Use Arrow Raise `either {}` for error handling
5. **Test properties**: Verify compression reduces size, preserves system messages
6. **Cache tokens**: Keep `enableTokenCaching = true` for performance
7. **Validate early**: Check parameters before API calls
8. **Profile overhead**: Measure compression latency in your use case

---

**Next Steps**:
- Read [spec.md](./spec.md) for requirements and user stories
- Read [research.md](./research.md) for Koog integration details
- Read [data-model.md](./data-model.md) for type definitions
- Check [contracts/](./contracts/) for implementation interfaces
- Run `/speckit.tasks` to see implementation tasks
