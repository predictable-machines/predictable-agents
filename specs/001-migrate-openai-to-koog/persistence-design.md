# Agent Persistence & Conversation Management Design

**Date**: 2025-10-16
**Feature**: Koog-based Persistence and Context Window Management
**Status**: Design Phase

---

## Research Summary

### Koog's Capabilities

**1. History Compression Strategies**
- `WholeHistory`: Compresses entire conversation into TL;DR
- `FromLastNMessages(n)`: Keeps only last N messages
- `FromTimestamp(ts)`: Keeps messages from timestamp
- `Chunked(size)`: Processes in chunks

**2. Persistence Layer**
- `PersistenceStorageProvider<F>` interface
- `AgentCheckpointData` stores: messageHistory, nodeId, lastInput, version
- Implementations: InMemory, File, SQL (H2, PostgreSQL, MySQL)

**3. Key Insights**
- Koog's persistence works at AIAgent framework level (not LLMClient)
- All operations maintain immutability
- Uses LLM itself to generate summaries

---

## Design Goals

1. **Backward Compatibility**: Existing OpenAIProvider API remains unchanged
2. **Opt-in Features**: Users choose to enable persistence/compression
3. **Immutability**: Respect immutable message lists
4. **Simplicity**: Lightweight layer, not full AIAgent framework adoption
5. **Testing**: Comprehensive tests for context window scenarios

---

## Architecture

### Option A: Lightweight Persistence Layer (CHOSEN)

Create optional extension on top of existing OpenAIProvider:

```
┌─────────────────────────────────────┐
│  User Code                          │
│  (Agent<T>.of(...))                 │
└──────────────┬──────────────────────┘
               │
               ├─ Without Persistence (current)
               │  └─> OpenAIProvider.chatCompletion(...)
               │
               └─ With Persistence (new)
                  └─> PersistentAgent.chatCompletion(...)
                      ├─> Load checkpoint
                      ├─> Compress history if needed
                      ├─> OpenAIProvider.chatCompletion(...)
                      └─> Save checkpoint
```

### Option B: Full AIAgent Adoption (REJECTED)

Would require complete rewrite and break existing API.

---

## Implementation Design

### 1. Persistence Layer

**ConversationCheckpoint** (lightweight version of AgentCheckpointData):

```kotlin
package predictable.agent.persistence

import arrow.core.raise.Raise
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import predictable.agent.Message

@Serializable
data class ConversationCheckpoint(
    val checkpointId: String,
    val conversationId: String,  // User-defined conversation ID
    val messageHistory: List<Message>,
    val createdAt: Instant,
    val metadata: Map<String, String> = emptyMap()
)

interface PersistenceProvider {
    context(_: Raise<PersistenceError>)
    suspend fun saveCheckpoint(checkpoint: ConversationCheckpoint)

    context(_: Raise<PersistenceError>)
    suspend fun loadLatestCheckpoint(conversationId: String): ConversationCheckpoint?

    context(_: Raise<PersistenceError>)
    suspend fun loadCheckpoint(checkpointId: String): ConversationCheckpoint?

    context(_: Raise<PersistenceError>)
    suspend fun listCheckpoints(conversationId: String): List<ConversationCheckpoint>
}

sealed interface PersistenceError {
    val message: String

    data class SaveFailed(override val message: String) : PersistenceError
    data class LoadFailed(override val message: String) : PersistenceError
    data class NotFound(val id: String, override val message: String = "Checkpoint not found: $id") : PersistenceError
}
```

**Implementations**:
- `InMemoryPersistenceProvider`: For testing
- `FilePersistenceProvider`: JSON files (simple, no external deps)

---

### 2. History Compression Layer

**HistoryCompressor** (simplified version of Koog's strategies):

```kotlin
package predictable.agent.history

import arrow.core.raise.Raise
import predictable.agent.Message
import predictable.agent.Model
import predictable.agent.providers.openai.OpenAIProvider

sealed interface CompressionStrategy {
    /**
     * Strategy that keeps last N messages plus all system messages
     */
    data class KeepLastN(val n: Int) : CompressionStrategy

    /**
     * Strategy that generates TL;DR summary using LLM
     * Preserves: all system messages, first user message, TL;DR of rest
     */
    object GenerateSummary : CompressionStrategy

    /**
     * Strategy based on token count (approximate)
     * Compresses when approaching maxTokens
     */
    data class TokenLimit(val maxTokens: Int) : CompressionStrategy
}

interface HistoryCompressor {
    context(_: Raise<CompressionError>)
    suspend fun compress(
        messages: List<Message>,
        strategy: CompressionStrategy,
        provider: OpenAIProvider,
        model: Model
    ): List<Message>
}

sealed interface CompressionError {
    val message: String

    data class CompressionFailed(override val message: String) : CompressionError
    data class InvalidStrategy(override val message: String) : CompressionError
}
```

---

### 3. PersistentAgent Wrapper

**PersistentAgent** (wraps existing Agent):

```kotlin
package predictable.agent.persistence

import arrow.core.raise.Raise
import predictable.agent.*
import predictable.agent.history.CompressionStrategy
import predictable.agent.history.HistoryCompressor
import predictable.agent.providers.openai.OpenAIProvider
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.datetime.Clock

class PersistentAgent(
    private val provider: OpenAIProvider,
    private val persistenceProvider: PersistenceProvider,
    private val historyCompressor: HistoryCompressor,
    private val compressionStrategy: CompressionStrategy? = null,  // null = no compression
    private val clock: Clock = Clock.System
) {
    /**
     * Chat completion with persistence
     * @param conversationId User-defined ID to group messages
     * @param messages Current messages (immutable input)
     * @param model Model to use
     * @param tools Tools available
     * @param parameters Request parameters
     * @param toolCallBack Tool execution callback
     * @return Response with updated message history
     */
    @OptIn(ExperimentalUuidApi::class)
    context(_: Raise<AgentError>)
    suspend fun chatCompletion(
        conversationId: String,
        messages: List<Message>,
        model: Model,
        tools: List<AI<*, *>> = emptyList(),
        parameters: RequestParameters = RequestParameters(),
        toolCallBack: ToolCallback? = null
    ): PersistentAgentResponse.Text {
        // 1. Load existing checkpoint
        val checkpoint = persistenceProvider.loadLatestCheckpoint(conversationId)

        // 2. Merge with new messages (checkpoint history + new messages)
        val fullHistory = (checkpoint?.messageHistory ?: emptyList()) + messages

        // 3. Compress if strategy is set
        val compressedHistory = compressionStrategy?.let {
            historyCompressor.compress(fullHistory, it, provider, model).bind()
        } ?: fullHistory

        // 4. Call provider
        val response = provider.chatCompletion(
            model = model,
            messages = compressedHistory,
            tools = tools,
            parameters = parameters,
            toolCallBack = toolCallBack
        )

        // 5. Create updated history (compressed + response messages)
        val updatedHistory = compressedHistory + response.messages

        // 6. Save checkpoint
        val newCheckpoint = ConversationCheckpoint(
            checkpointId = Uuid.random().toString(),
            conversationId = conversationId,
            messageHistory = updatedHistory,
            createdAt = clock.now(),
            metadata = mapOf("model" to model.name)
        )
        persistenceProvider.saveCheckpoint(newCheckpoint).bind()

        // 7. Return response with full history
        return PersistentAgentResponse.Text(
            value = response.value,
            metadata = response.metadata,
            messages = updatedHistory,  // Full history for transparency
            checkpointId = newCheckpoint.checkpointId
        )
    }

    // Similar methods for structured output and streaming...
}

// Extended response type with checkpoint info
sealed interface PersistentAgentResponse {
    data class Text(
        val value: String,
        val metadata: AgentMetadata,
        val messages: List<Message>,
        val checkpointId: String
    ) : PersistentAgentResponse

    // ... other response types
}

sealed interface AgentError {
    val message: String

    data class PersistenceFailed(val error: PersistenceError, override val message: String) : AgentError
    data class CompressionFailed(val error: CompressionError, override val message: String) : AgentError
    data class ProviderFailed(val error: ProviderError, override val message: String) : AgentError
}
```

---

## Usage Examples

### Basic Persistence (No Compression)

```kotlin
val provider = OpenAIProvider(baseUrl = null, apiKey = apiKey)
val persistence = InMemoryPersistenceProvider()
val compressor = DefaultHistoryCompressor()

val persistentAgent = PersistentAgent(
    provider = provider,
    persistenceProvider = persistence,
    historyCompressor = compressor,
    compressionStrategy = null  // No compression
)

// First message in conversation
val response1 = persistentAgent.chatCompletion(
    conversationId = "user-123-session-1",
    messages = listOf(Message.User("Hello, who are you?")),
    model = Model("gpt-4")
)
// response1.messages contains: [User("Hello..."), Assistant("I am...")]

// Continue conversation (history automatically loaded)
val response2 = persistentAgent.chatCompletion(
    conversationId = "user-123-session-1",  // Same ID
    messages = listOf(Message.User("What can you do?")),
    model = Model("gpt-4")
)
// response2.messages contains full history:
// [User("Hello..."), Assistant("I am..."), User("What can..."), Assistant("I can...")]
```

### With Context Window Management

```kotlin
val persistentAgent = PersistentAgent(
    provider = provider,
    persistenceProvider = persistence,
    historyCompressor = compressor,
    compressionStrategy = CompressionStrategy.TokenLimit(maxTokens = 4000)
)

// After many messages, history is automatically compressed
val response = persistentAgent.chatCompletion(
    conversationId = "long-conversation",
    messages = listOf(Message.User("Summarize our discussion")),
    model = Model("gpt-4")
)
// Internally: old messages compressed to TL;DR, recent messages preserved
```

### With Manual Compression

```kotlin
val persistentAgent = PersistentAgent(
    provider = provider,
    persistenceProvider = persistence,
    historyCompressor = compressor,
    compressionStrategy = CompressionStrategy.KeepLastN(n = 10)  // Only keep last 10
)
```

---

## Test Requirements

### 1. Persistence Tests

**Test Cases**:
- Save and load checkpoint
- Multiple checkpoints for same conversation
- Checkpoint not found handling
- Concurrent save/load operations
- File persistence (JSON serialization)
- In-memory persistence (for testing)

**File**: `agents/src/jvmTest/kotlin/agent/persistence/PersistenceProviderTest.kt`

### 2. History Compression Tests

**Test Cases**:
- KeepLastN strategy preserves correct messages
- GenerateSummary creates valid TL;DR
- TokenLimit triggers at right threshold
- System messages always preserved
- Immutable message lists (no mutation)
- Empty history handling
- Single message handling

**File**: `agents/src/jvmTest/kotlin/agent/history/HistoryCompressorTest.kt`

### 3. Persistent Agent Integration Tests

**Test Cases**:
- Basic conversation flow (multi-turn)
- Conversation continuation from checkpoint
- Context window exceeds limit (auto-compression)
- Immutable message handling (input not modified)
- Tool calling with persistence
- Structured output with persistence
- Streaming with persistence (if supported)
- Error handling (persistence fails, compression fails)
- Concurrent conversations (different IDs)

**File**: `agents/src/jvmTest/kotlin/agent/persistence/PersistentAgentTest.kt`

### 4. Context Window Tests

**Specific Scenarios**:

**Test 1: Gradual Context Growth**
- Send 100 short messages
- Verify compression triggers at threshold
- Verify compressed history < original
- Verify conversation quality maintained

**Test 2: Large Single Message**
- Send message with 10K tokens
- Verify it's not lost in compression
- Verify system can handle edge case

**Test 3: Immutable Input Guarantee**
- Create message list
- Pass to PersistentAgent
- Verify original list unchanged
- Verify no side effects

**Test 4: Checkpoint Recovery**
- Simulate crash after message 50
- Load checkpoint
- Continue conversation
- Verify no data loss

**File**: `agents/src/jvmTest/kotlin/agent/persistence/ContextWindowTest.kt`

---

## Implementation Tasks

1. **Core Types** (30 min)
   - ConversationCheckpoint data class
   - PersistenceProvider interface
   - PersistenceError sealed interface
   - CompressionStrategy sealed interface
   - HistoryCompressor interface
   - AgentError sealed interface

2. **In-Memory Persistence** (30 min)
   - InMemoryPersistenceProvider implementation
   - Thread-safe map-based storage
   - Basic CRUD operations

3. **File Persistence** (1 hour)
   - FilePersistenceProvider implementation
   - JSON serialization with kotlinx.serialization
   - File-based storage in ~/.predictable/checkpoints/

4. **History Compressor** (2 hours)
   - DefaultHistoryCompressor implementation
   - KeepLastN strategy
   - GenerateSummary strategy (uses LLM)
   - TokenLimit strategy (approximate token counting)

5. **PersistentAgent** (2 hours)
   - chatCompletion with persistence
   - chatCompletionStructured with persistence
   - Checkpoint load/save logic
   - History compression integration
   - Error handling

6. **Tests** (4 hours)
   - PersistenceProviderTest (in-memory + file)
   - HistoryCompressorTest
   - PersistentAgentTest
   - ContextWindowTest
   - Integration test with real API (if available)

---

## Success Criteria

1. ✅ All new code follows constitutional requirements (≤10 lines per function)
2. ✅ All operations preserve immutability
3. ✅ Comprehensive test coverage (>90%)
4. ✅ Tests pass without API access (mocked)
5. ✅ Documentation updated with examples
6. ✅ Backward compatible (existing code unchanged)
7. ✅ Performance acceptable (<10ms overhead for persistence)
8. ✅ Context window tests demonstrate effective compression

---

## Future Enhancements

1. **Distributed Persistence**: Redis/PostgreSQL backends
2. **Advanced Compression**: Semantic chunking, entity extraction
3. **Conversation Branching**: Support multiple branches from checkpoint
4. **Metadata Search**: Query checkpoints by metadata
5. **Full AIAgent Adoption**: Migrate to Koog's AIAgent framework

---

## References

- [Koog HistoryCompressionStrategies](/tmp/koog/agents/agents-core/src/commonMain/kotlin/ai/koog/agents/core/dsl/extension/HistoryCompressionStrategies.kt)
- [Koog AgentCheckpointData](/tmp/koog/agents/agents-features/agents-features-snapshot/src/commonMain/kotlin/ai/koog/agents/snapshot/feature/AgentCheckpointData.kt)
- [Koog InMemoryPersistenceProvider](/tmp/koog/agents/agents-features/agents-features-snapshot/src/commonMain/kotlin/ai/koog/agents/snapshot/providers/InMemoryPersistenceStorageProvider.kt)
- [Original Migration Spec](./spec.md)
- [Implementation Tasks](./tasks.md)
