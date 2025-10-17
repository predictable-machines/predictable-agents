# Koog Integration Research

**Feature**: Developer-Controlled History Management
**Branch**: `003-history-management-control`
**Date**: 2025-10-16
**Research Source**: `/tmp/koog` repository

## Executive Summary

This document contains research findings for integrating Koog AI Framework's native compression, history management, and persistence features into our AbstractAgent through delegation patterns. All findings are based on direct code inspection of Koog's implementation.

**Key Decision**: Wrap Koog's `AIAgent` system within `AbstractAgent` to gain access to `AIAgentLLMContext` which provides `writeSession` for compression operations.

## 1. Koog AIAgent Construction Patterns

### Factory Methods

Koog provides multiple factory methods via `AIAgent.Companion.invoke()`:

```kotlin
// Method 1: Full control with strategy and config
AIAgent<Input, Output>(
  promptExecutor: PromptExecutor,
  agentConfig: AIAgentConfig,
  strategy: AIAgentGraphStrategy<Input, Output>,
  toolRegistry: ToolRegistry = ToolRegistry.EMPTY,
  id: String? = null,
  clock: Clock = Clock.System,
  installFeatures: FeatureContext.() -> Unit = {}
)

// Method 2: Simplified with LLM model and system prompt
AIAgent(
  promptExecutor: PromptExecutor,
  llmModel: LLModel,
  strategy: AIAgentGraphStrategy<String, String> = singleRunStrategy(),
  toolRegistry: ToolRegistry = ToolRegistry.EMPTY,
  id: String? = null,
  systemPrompt: String = "",
  temperature: Double = 1.0,
  numberOfChoices: Int = 1,
  maxIterations: Int = 50,
  installFeatures: FeatureContext.() -> Unit = {}
)
```

**Location**: `/tmp/koog/agents/agents-core/src/commonMain/kotlin/ai/koog/agents/core/agent/AIAgent.kt:169-403`

### AIAgentConfig Structure

```kotlin
class AIAgentConfig(
  val prompt: Prompt,  // Initial prompt with system message
  val model: LLModel,  // Model configuration
  val maxAgentIterations: Int,  // Iteration limit
  val missingToolsConversionStrategy: MissingToolsConversionStrategy = Missing(ToolCallDescriber.JSON)
)
```

**Location**: `/tmp/koog/agents/agents-core/src/commonMain/kotlin/ai/koog/agents/core/agent/config/AIAgentConfig.kt:24-30`

### GraphAIAgent Internal Structure

The `GraphAIAgent` creates an `AIAgentLLMContext` during `prepareContext()`:

```kotlin
AIAgentLLMContext(
  tools = toolRegistry.tools.map { it.descriptor },
  toolRegistry = toolRegistry,
  prompt = agentConfig.prompt,
  model = agentConfig.model,
  promptExecutor = PromptExecutorProxy(
    executor = promptExecutor,
    pipeline = pipeline,
    runId = runId
  ),
  environment = preparedEnvironment,
  config = agentConfig,
  clock = clock
)
```

**Location**: `/tmp/koog/agents/agents-core/src/commonMain/kotlin/ai/koog/agents/core/agent/GraphAIAgent.kt:123-136`

### Integration Pattern for Our AbstractAgent

**Current Pattern** (to be replaced):
```kotlin
AbstractAgent → AgentProvider → LLMClient.execute()
```

**New Pattern** (wrapping Koog):
```kotlin
AbstractAgent → Koog AIAgent → AIAgentLLMContext → writeSession { ... }
                     ↓
        Compression, Memory, Persistence
```

**Key Components**:
1. Create `AIAgent` using factory with our config
2. Access `AIAgentLLMContext` via agent's context (exposed in `Running` state)
3. Use `writeSession {}` to apply compression
4. Execute requests through agent's `run()` method

## 2. Koog Compression Strategy Integration

### HistoryCompressionStrategy Sealed Class

Koog provides 5 compression strategies:

```kotlin
sealed class HistoryCompressionStrategy {

  // Compress entire history to single TL;DR
  object WholeHistory : HistoryCompressionStrategy()

  // Keep last N messages, compress rest
  data class FromLastNMessages(val n: Int) : HistoryCompressionStrategy()

  // Compress history in chunks
  data class Chunked(val chunkSize: Int) : HistoryCompressionStrategy()

  // Compress messages before timestamp
  data class FromTimestamp(val timestamp: Instant) : HistoryCompressionStrategy()

  // Compress with multiple system messages
  object WholeHistoryMultipleSystemMessages : HistoryCompressionStrategy()

  abstract suspend fun compress(
    llmSession: AIAgentLLMWriteSession,
    memoryMessages: List<Message>
  )
}
```

**Location**: `/tmp/koog/agents/agents-core/src/commonMain/kotlin/ai/koog/agents/core/dsl/extension/HistoryCompressionStrategies.kt:19-318`

### Compression Invocation Pattern

**Extension Function**:
```kotlin
suspend fun AIAgentLLMWriteSession.replaceHistoryWithTLDR(
  strategy: HistoryCompressionStrategy = HistoryCompressionStrategy.WholeHistory,
  preserveMemory: Boolean = true
)
```

**Location**: `/tmp/koog/agents/agents-core/src/commonMain/kotlin/ai/koog/agents/core/dsl/extension/AIAgentLLMActions.kt:112-127`

**Usage Example**:
```kotlin
koogAgent.llm.writeSession {
  replaceHistoryWithTLDR(
    strategy = HistoryCompressionStrategy.LastNMessages(10),
    preserveMemory = true
  )
}
```

### How Compression Works Internally

1. **TL;DR Generation** (`compressPromptIntoTLDR`):
   - Drops trailing tool calls
   - Adds user message: "Please summarize the conversation in TL;DR format"
   - Calls `requestLLMWithoutTools()` to get summary
   - Returns summary as `List<Message.Response>`

2. **History Composition** (`composeMessageHistory`):
   - Preserves all system messages
   - Preserves first user message (if present)
   - Adds memory messages
   - Sorts by timestamp
   - Appends TL;DR messages

3. **Prompt Update**:
   - `llmSession.prompt = llmSession.prompt.withMessages { compressedMessages }`

**Key Insight**: Compression uses LLM to generate summaries, not string truncation.

## 3. Koog Message Type Conversions

### Koog's Message Structure

```kotlin
@Serializable
sealed interface Message {
  val content: String
  val role: Role  // System, User, Assistant, Tool
  val metaInfo: MessageMetaInfo  // timestamp, token counts

  @Serializable
  data class User(
    override val content: String,
    override val metaInfo: RequestMetaInfo,
    override val attachments: List<Attachment> = emptyList()
  ) : Request, WithAttachments

  @Serializable
  data class Assistant(
    override val content: String,
    override val metaInfo: ResponseMetaInfo,
    override val attachments: List<Attachment> = emptyList(),
    val finishReason: String? = null
  ) : Response, WithAttachments

  @Serializable
  data class System(
    override val content: String,
    override val metaInfo: RequestMetaInfo
  ) : Request

  sealed interface Tool : Message {
    val id: String?
    val tool: String

    data class Call(
      override val id: String?,
      override val tool: String,
      override val content: String,
      override val metaInfo: ResponseMetaInfo
    ) : Tool, Response

    data class Result(
      override val id: String?,
      override val tool: String,
      override val content: String,
      override val metaInfo: RequestMetaInfo
    ) : Tool, Request
  }
}
```

**Location**: `/tmp/koog/prompt/prompt-model/src/commonMain/kotlin/ai/koog/prompt/message/Message.kt:1-339`

### Our Message Structure

```kotlin
@Serializable
data class Message(
  val role: MessageRole,  // System, Assistant, User, Custom(String)
  val content: String,
  val name: String? = null,
  val toolCalls: List<ToolCallRequest>? = null,
  val toolCallId: String? = null
)
```

**Location**: `/Users/raulraja/predictable-machines/predictable-agents/agents/src/commonMain/kotlin/agent/Message.kt:22-28`

### Conversion Strategy

**Our Message → Koog Message**:
```kotlin
fun predictable.agent.Message.toKoogMessage(clock: Clock): ai.koog.prompt.message.Message =
  when (role) {
    MessageRole.System -> ai.koog.prompt.message.Message.System(
      content = content,
      metaInfo = RequestMetaInfo.create(clock)
    )
    MessageRole.User -> ai.koog.prompt.message.Message.User(
      content = content,
      metaInfo = RequestMetaInfo.create(clock)
    )
    MessageRole.Assistant -> {
      if (toolCalls.isNullOrEmpty()) {
        ai.koog.prompt.message.Message.Assistant(
          content = content,
          metaInfo = ResponseMetaInfo.create(clock)
        )
      } else {
        // Map toolCalls to Tool.Call messages
        // Note: Koog requires separate Tool.Call messages
      }
    }
    is MessageRole.Custom -> {
      // Map to System with custom name in content
      ai.koog.prompt.message.Message.System(
        content = "[$role] $content",
        metaInfo = RequestMetaInfo.create(clock)
      )
    }
  }
```

**Koog Message → Our Message**:
```kotlin
fun ai.koog.prompt.message.Message.toPredictableMessage(): predictable.agent.Message =
  when (this) {
    is ai.koog.prompt.message.Message.System -> predictable.agent.Message.system(content)
    is ai.koog.prompt.message.Message.User -> predictable.agent.Message.user(content)
    is ai.koog.prompt.message.Message.Assistant -> predictable.agent.Message.assistant(content)
    is ai.koog.prompt.message.Message.Tool.Call -> predictable.agent.Message(
      role = MessageRole.Assistant,
      content = "",
      toolCalls = listOf(ToolCallRequest(id = id, type = tool, function = ToolCallRequest.Function(tool, content)))
    )
    is ai.koog.prompt.message.Message.Tool.Result -> predictable.agent.Message(
      role = MessageRole.User,  // Tool results come back as user messages in our model
      content = content,
      toolCallId = id
    )
  }
```

**Important Notes**:
- Koog uses separate `Message.Tool.Call` and `Message.Tool.Result` types
- Our model combines tool calls in `toolCalls` list on Assistant messages
- Koog's messages have `metaInfo` with timestamps and token counts
- We need to preserve token counts when converting back

## 4. Koog Session Lifecycle Management

### AIAgentLLMContext Thread-Safety

```kotlin
class AIAgentLLMContext(
  tools: List<ToolDescriptor>,
  prompt: Prompt,
  model: LLModel,
  promptExecutor: PromptExecutor,
  environment: AIAgentEnvironment,
  config: AIAgentConfig,
  clock: Clock
) {
  private val rwLock = RWLock()

  suspend fun <T> writeSession(block: suspend AIAgentLLMWriteSession.() -> T): T =
    rwLock.withWriteLock {
      val session = AIAgentLLMWriteSession(...)
      session.use {
        val result = it.block()
        // Update context after session
        this.prompt = it.prompt
        this.tools = it.tools
        this.model = it.model
        result
      }
    }

  suspend fun <T> readSession(block: suspend AIAgentLLMReadSession.() -> T): T =
    rwLock.withReadLock {
      val session = AIAgentLLMReadSession(...)
      session.use { block(it) }
    }
}
```

**Location**: `/tmp/koog/agents/agents-core/src/commonMain/kotlin/ai/koog/agents/core/agent/context/AIAgentLLMContext.kt:51-176`

### AIAgentLLMWriteSession

```kotlin
class AIAgentLLMWriteSession(
  environment: AIAgentEnvironment,
  executor: PromptExecutor,
  tools: List<ToolDescriptor>,
  toolRegistry: ToolRegistry,
  prompt: Prompt,
  model: LLModel,
  config: AIAgentConfig,
  clock: Clock
) : AIAgentLLMSession(executor, tools, prompt, model, config) {

  // Mutable properties validated by ActiveProperty delegate
  override var prompt: Prompt by ActiveProperty(prompt) { isActive }
  override var tools: List<ToolDescriptor> by ActiveProperty(tools) { isActive }
  override var model: LLModel by ActiveProperty(model) { isActive }

  // Key methods for compression
  fun updatePrompt(body: PromptBuilder.() -> Unit)
  fun rewritePrompt(body: (prompt: Prompt) -> Prompt)
  suspend fun requestLLMWithoutTools(): Message.Response
  fun requestLLMStreaming(definition: StructuredDataDefinition? = null): Flow<StreamFrame>
}
```

**Location**: `/tmp/koog/agents/agents-core/src/commonMain/kotlin/ai/koog/agents/core/agent/session/AIAgentLLMWriteSession.kt:40-492`

### Session Lifecycle for Compression

**Pattern**:
```kotlin
// 1. Access LLM context from running agent
val llmContext: AIAgentLLMContext = ... // from agent's running state

// 2. Open write session
llmContext.writeSession {
  // 3. Apply compression
  replaceHistoryWithTLDR(
    strategy = HistoryCompressionStrategy.WholeHistory
  )

  // 4. Session automatically commits changes to context on close
}

// 5. Continue using agent with compressed history
val response = koogAgent.run(input)
```

**Key Insights**:
- Write sessions use RWLock for thread safety
- Changes are committed atomically on session close
- Sessions are `Closeable` and auto-commit via `use {}`
- Compression happens BEFORE any LLM request
- Streaming and compression are separate operations

## 5. Koog Tokenization Integration

### PromptTokenizer Interface

```kotlin
interface PromptTokenizer {
  fun tokenCountFor(message: Message): Int
  fun tokenCountFor(prompt: Prompt): Int
}
```

**Location**: `/tmp/koog/prompt/prompt-tokenizer/src/commonMain/kotlin/ai/koog/prompt/tokenizer/PromptTokenizer.kt:10-26`

### Implementations

**OnDemandTokenizer** - No caching:
```kotlin
class OnDemandTokenizer(private val tokenizer: Tokenizer) : PromptTokenizer {
  override fun tokenCountFor(message: Message): Int =
    tokenizer.countTokens(message.content)

  override fun tokenCountFor(prompt: Prompt): Int =
    prompt.messages.sumOf(::tokenCountFor)
}
```

**CachingTokenizer** - With caching:
```kotlin
class CachingTokenizer(private val tokenizer: Tokenizer) : PromptTokenizer {
  internal val cache = mutableMapOf<Message, Int>()

  override fun tokenCountFor(message: Message): Int = cache.getOrPut(message) {
    tokenizer.countTokens(message.content)
  }

  override fun tokenCountFor(prompt: Prompt): Int =
    prompt.messages.sumOf(::tokenCountFor)

  fun clearCache()
}
```

**Location**: `/tmp/koog/prompt/prompt-tokenizer/src/commonMain/kotlin/ai/koog/prompt/tokenizer/PromptTokenizer.kt:38-110`

### Integration Pattern for maxTokens

```kotlin
// 1. Get tokenizer from agent config or create one
val tokenizer = CachingTokenizer(TiktokenEncoder(model))

// 2. Estimate tokens before compression
val tokensBefore = tokenizer.tokenCountFor(llmContext.prompt)

// 3. Check against maxTokens
if (tokensBefore > parameters.maxTokens) {
  // Apply compression
  llmContext.writeSession {
    replaceHistoryWithTLDR(parameters.compressionStrategy!!)
  }
}

// 4. Verify after compression
val tokensAfter = tokenizer.tokenCountFor(llmContext.prompt)
```

**Key Insights**:
- Token counting is separate from compression
- `CachingTokenizer` recommended for repeated counts
- Token counts stored in `ResponseMetaInfo` after LLM calls
- Use for pre-compression validation, not automatic triggers

## 6. Error Mapping Strategy

### Koog Exceptions to Our Typed Errors

**Compression Errors**:
```kotlin
sealed class CompressionError {
  data class InvalidTokenLimit(
    val systemPromptTokens: Int,
    val maxTokens: Int
  ) : CompressionError()

  data class CompressionFailed(
    val strategy: String,
    val cause: Throwable
  ) : CompressionError()

  data class InsufficientHistory(
    val messageCount: Int,
    val minimum: Int
  ) : CompressionError()
}
```

**Mapping Pattern with Arrow Raise**:
```kotlin
context(Raise<CompressionError>)
suspend fun compressHistory(
  strategy: HistoryCompressionStrategy,
  messages: List<Message>
): List<Message> {
  withError({ e: Throwable ->
    CompressionError.CompressionFailed(strategy.toString(), e)
  }) {
    llmContext.writeSession {
      replaceHistoryWithTLDR(strategy)
      prompt.messages.map { it.toPredictableMessage() }
    }
  }
}
```

## 7. Decision Matrix: Wrapping vs. Adapting

| Aspect | Wrap AIAgent (Selected) | Adapt Strategies Only |
|--------|-------------------------|----------------------|
| **Compression Access** | ✅ Native `replaceHistoryWithTLDR()` | ❌ Reimplementation required |
| **Memory Features** | ✅ Available if needed later | ❌ Not accessible |
| **Persistence** | ✅ Checkpoints included | ❌ Must build ourselves |
| **Session Management** | ✅ Thread-safe via RWLock | ❌ Must implement locking |
| **Token Counting** | ✅ Integrated | ⚠️  Must wire separately |
| **Code Complexity** | ⚠️  Wrapper layer needed | ✅ Simpler initially |
| **Future Extensibility** | ✅ Can expose more features | ❌ Limited to what we build |
| **Maintenance Burden** | ✅ Koog handles updates | ❌ Must track Koog changes |

**Conclusion**: Wrapping `AIAgent` provides better long-term value despite initial complexity.

## 8. Integration Checklist

- [x] Understand AIAgent construction patterns
- [x] Understand compression strategy invocation
- [x] Design message type converters
- [x] Understand session lifecycle (writeSession)
- [x] Understand token counting integration
- [ ] Design KoogAgentFactory (creates AIAgent instances)
- [ ] Design MessageConverter (bidirectional conversion)
- [ ] Design KoogCompressionAdapter (delegates to replaceHistoryWithTLDR)
- [ ] Design HistoryManager (applies precedence: compression → tokens → size)
- [ ] Extend RequestParameters with compression fields
- [ ] Refactor AbstractAgent to wrap AIAgent
- [ ] Implement tests with property-based testing

## 9. Next Steps

1. **Create data-model.md**: Define RequestParameters extensions, CompressionError, KoogAgentWrapper
2. **Create contracts/**: Define KoogCompressionAdapter and HistoryManager interfaces
3. **Create quickstart.md**: Developer guide with usage examples
4. **Run `/speckit.tasks`**: Generate concrete implementation tasks
5. **Begin implementation**: Start with Phase 1 tasks (T001-T008)

---

**Research Complete**: 2025-10-16
**Constitutional Compliance**: All future tasks MUST follow this research pattern - read Koog code before implementing.
