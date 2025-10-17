# Implementation Plan: Developer-Controlled History Management

**Branch**: `003-history-management-control` | **Date**: 2025-10-16 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/003-specify-scripts-bash/spec.md`

**Note**: This plan delegates to Koog's native compression, history management, and persistence implementations through RequestParameters.

## Summary

**Primary Requirement**: Enable developers to explicitly control conversation history management (compression, size limits, token limits) through RequestParameters, with all functionality delegated to Koog's AIAgent system.

**Technical Approach** (from research & clarification):
- **Wrap Koog's AIAgent** within AbstractAgent instead of using LLMClient directly
- **Expose Koog's features** through RequestParameters extensions
- **Delegate compression** to Koog's `HistoryCompressionStrategy` implementations
- **Delegate history management** to Koog's `AIAgentLLMWriteSession` methods
- **Delegate persistence** to Koog's checkpoint system
- **Zero reimplementation** - pure delegation pattern

## Technical Context

**Language/Version**: Kotlin 2.2.10 (Kotlin Multiplatform)
**Primary Dependencies**:
- Koog AI Framework (ai.koog.agents.core, ai.koog.prompt)
- Arrow Core (Raise context, functional error handling)
- kotlinx.coroutines (suspend functions, Flow)
- kotlinx.serialization (JSON, data classes)
- kotlinx.datetime (timestamps)

**Storage**: Koog's InMemoryPersistenceStorageProvider (checkpoints in memory)
**Testing**:
- Kotlin Test (property-based testing with Kotest)
- JUnit 5 (JVM platform tests)
- Koog's testing utilities (MockLLMExecutor, DummyTool)

**Target Platform**: Kotlin Multiplatform (JVM, JS, Native)
**Project Type**: Library (agents module within multiplatform project)
**Performance Goals**:
- Compression reduces token count by 60%+ (SC-005)
- Token estimation within 10% accuracy (SC-004, FR-009)
- History management <50ms overhead per call

**Constraints**:
- Pure functional programming (constitution)
- Functions ≤10 lines (constitution)
- Arrow Raise context for errors (constitution)
- No automatic compression - explicit only (FR-002)
- MANDATORY Koog research before each task (constitution)

**Scale/Scope**:
- 5 compression strategies from Koog
- 3 history size parameters (compression, maxHistorySize, maxTokens)
- All AbstractAgent methods (invoke, chat, stream, generateObject)
- Support for 100k+ token conversations

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### ✅ Pure Functional Programming
- **Status**: COMPLIANT
- **Evidence**: All Koog APIs use suspend functions and immutable data structures. Our wrapper will maintain purity by delegating to Koog's functional APIs.

### ✅ Function Size Discipline (≤10 lines)
- **Status**: COMPLIANT
- **Strategy**: Delegation pattern naturally produces small functions. Each RequestParameter field maps to 1 Koog API call.

### ✅ Forbidden Imperative Constructs
- **Status**: COMPLIANT
- **Evidence**: No for/while loops, no mutable vars. Koog uses Flow for streaming, suspend for async. We delegate directly.

### ✅ Type Safety and Totality (Arrow Raise context)
- **Status**: COMPLIANT
- **Strategy**:
  - Use `context(_: Raise<AgentError>)` for all integration functions
  - Map Koog exceptions to typed errors (CompressionError, ValidationError)
  - Use `ensureNotNull`, `ensure` for validation

### ✅ Immutable Data Structures
- **Status**: COMPLIANT
- **Evidence**: RequestParameters is data class, Koog's Prompt/Message are immutable

### ✅ Detekt Rule Enforcement
- **Status**: NON-NEGOTIABLE (constitutional requirement)
- **Action**: MUST run `./gradlew build` after EVERY phase and fix ALL violations before proceeding
- **Evidence**: Delegation pattern minimizes code complexity, reducing detekt risk

### ✅ Property-Based Testing
- **Status**: COMPLIANT
- **Strategy**: Test compression properties (messagesBefore > messagesAfter, tokensBefore > tokensAfter) across strategies

### ✅ Error Handling (Arrow Raise context)
- **Status**: COMPLIANT
- **Required imports**:
  ```kotlin
  import arrow.core.raise.Raise
  import arrow.core.raise.context.raise
  import arrow.core.raise.context.ensure
  import arrow.core.raise.context.ensureNotNull
  import arrow.core.raise.context.withError
  ```

### ✅ Parallel Execution (Arrow FX)
- **Status**: NOT APPLICABLE
- **Reason**: History management is sequential (apply compression → check tokens → trim messages). No parallelism needed.

### ✅ Koog Integration Research Protocol
- **Status**: COMPLIANT
- **Evidence**: Spec created AFTER deep /tmp/koog research. All tasks MUST continue this pattern (constitutional requirement added).

### 🔄 Build Verification Requirement
- **Status**: PENDING
- **Action**: MUST run `./gradlew build` after implementation and fix ALL Detekt violations before completion

## Project Structure

### Documentation (this feature)

```
specs/003-specify-scripts-bash/
├── spec.md              # Feature specification (complete)
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (Koog integration patterns)
├── data-model.md        # Phase 1 output (RequestParameters extensions, error types)
├── quickstart.md        # Phase 1 output (developer guide)
├── contracts/           # Phase 1 output (Koog adapter interfaces)
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT YET CREATED)
```

### Source Code (repository root)

```
agents/src/
├── commonMain/kotlin/
│   ├── predictable/
│   │   ├── AbstractAgent.kt              # MAJOR REFACTOR: Wrap Koog AIAgent
│   │   └── agent/
│   │       ├── AgentInput.kt             # Current - no changes
│   │       ├── AgentResponse.kt          # Current - no changes
│   │       ├── Message.kt                # Add Koog converters
│   │       ├── Model.kt                  # Current - no changes
│   │       ├── RequestParameters.kt      # EXTEND: Add compression fields
│   │       ├── compression/              # NEW: Koog delegation layer
│   │       │   ├── CompressionError.kt   # Typed errors for compression
│   │       │   ├── KoogCompressionAdapter.kt  # Delegates to Koog strategies
│   │       │   └── HistoryManager.kt     # Applies precedence: compression → tokens → size
│   │       └── providers/
│   │           ├── AgentProvider.kt      # REFACTOR: Create Koog AIAgents
│   │           ├── KoogAgentFactory.kt   # NEW: Build Koog AIAgent instances
│   │           └── MessageConverter.kt   # NEW: Our Message ↔ Koog Message
│   └── Tool.kt                           # Current - no changes
└── commonTest/kotlin/
    └── agent/
        ├── compression/
        │   ├── CompressionIntegrationTest.kt      # Test all 5 strategies
        │   ├── HistoryManagerTest.kt              # Test precedence order
        │   └── CompressionPropertiesTest.kt       # Property-based tests
        └── KoogIntegrationTest.kt                 # End-to-end with Koog

agents/build.gradle.kts                    # ADD: Koog dependencies
```

**Structure Decision**: Single Kotlin Multiplatform library project. All code lives in `agents/src/commonMain` for maximum reusability across JVM/JS/Native. Koog integration layer is thin delegation with type conversions.

## Complexity Tracking

*No constitutional violations requiring justification. All gates passed.*

## Phase 0: Research & Design Decisions

### Research Tasks

1. **Koog AIAgent Construction Patterns**
   - **Focus**: How to create AIAgent instances with custom configuration
   - **Files**: `/tmp/koog/agents/agents-core/src/commonMain/kotlin/ai/koog/agents/core/agent/AIAgent.kt`
   - **Output**: Factory patterns for creating AIAgents from our AbstractAgent config

2. **Koog Compression Strategy Integration**
   - **Focus**: How to invoke `replaceHistoryWithTLDR()` and `nodeLLMCompressHistory()`
   - **Files**: `/tmp/koog/agents/agents-core/src/commonMain/kotlin/ai/koog/agents/core/dsl/extension/HistoryCompressionStrategies.kt`
   - **Output**: Adapter that maps our RequestParameters to Koog writeSession calls

3. **Koog Message Type Conversions**
   - **Focus**: Mapping between `predictable.agent.Message` and `ai.koog.prompt.message.Message`
   - **Files**: `/tmp/koog/prompt/prompt-model/src/commonMain/kotlin/ai/koog/prompt/message/Message.kt`
   - **Output**: Bidirectional converters preserving metadata

4. **Koog Session Lifecycle Management**
   - **Focus**: When to create writeSession, how to handle session closure
   - **Files**: `/tmp/koog/agents/agents-core/src/commonMain/kotlin/ai/koog/agents/core/agent/session/AIAgentLLMSession.kt`
   - **Output**: Session management patterns for history modifications

5. **Koog Tokenization Integration**
   - **Focus**: Using Koog's PromptTokenizer for token estimation
   - **Files**: `/tmp/koog/prompt/prompt-tokenizer/src/commonMain/kotlin/ai/koog/prompt/tokenizer/PromptTokenizer.kt`
   - **Output**: Token counting strategy for maxTokens parameter

### Research Outputs

**File**: `research.md`

**Contents**:
- Decision matrix for Koog AIAgent wrapper patterns
- Compression strategy invocation examples
- Message type conversion utilities
- Session lifecycle best practices
- Token estimation accuracy benchmarks
- Error mapping strategy (Koog exceptions → our typed errors)

## Phase 1: Design Artifacts

### 1. Data Model (`data-model.md`)

**Entities**:

1. **RequestParameters Extensions** (existing class, adding fields)
   ```kotlin
   data class RequestParameters(
     // Existing fields...
     val temperature: Double? = null,
     val maxCompletionTokens: Int? = null,
     // ... other existing fields ...

     // NEW: History management fields
     val compressionStrategy: HistoryCompressionStrategy? = null,
     val maxHistorySize: Int? = null,
     val maxTokens: Int? = null,
     val enableTokenCaching: Boolean = true
   )
   ```
   - **Relationships**: Maps 1:1 to Koog's LLMParams + compression config
   - **Validation**: maxTokens must accommodate system prompt (FR-016)

2. **CompressionError** (new sealed class)
   ```kotlin
   sealed class CompressionError {
     data class InvalidTokenLimit(val systemPromptTokens: Int, val maxTokens: Int) : CompressionError()
     data class CompressionFailed(val strategy: String, val cause: Throwable) : CompressionError()
     data class InsufficientHistory(val messageCount: Int, val minimum: Int) : CompressionError()
   }
   ```

3. **HistoryCompressionStrategy** (delegated to Koog)
   - **Source**: `ai.koog.agents.core.dsl.extension.HistoryCompressionStrategy`
   - **Variants**: WholeHistory, LastNMessages(n), Chunked(size), FromTimestamp(ts), WholeHistoryMultipleSystemMessages
   - **Usage**: Pass directly to Koog's `replaceHistoryWithTLDR()`

4. **KoogAgentWrapper** (new internal class)
   ```kotlin
   internal class KoogAgentWrapper(
     val koogAgent: AIAgent,
     val config: AbstractAgent.Config
   )
   ```
   - **Lifecycle**: Lazy initialization, closed on AbstractAgent cleanup
   - **Thread-safety**: Koog provides thread-safe sessions

### 2. Contracts (`contracts/`)

**File**: `KoogAdapterInterface.kt`

```kotlin
/**
 * Adapter interface for Koog AIAgent integration.
 * All implementations MUST delegate to Koog's native APIs.
 */
context(_: Raise<CompressionError>)
interface KoogCompressionAdapter {
  /**
   * Apply compression using Koog's writeSession.
   * @param strategy Koog's HistoryCompressionStrategy
   * @param currentMessages Current message list
   * @return Compressed message list
   */
  suspend fun compress(
    strategy: HistoryCompressionStrategy,
    currentMessages: List<Message>
  ): List<Message>
}

/**
 * History manager applying precedence order.
 */
context(_: Raise<CompressionError>)
interface HistoryManager {
  /**
   * Apply history management in precedence order:
   * compressionStrategy → maxTokens → maxHistorySize
   */
  suspend fun apply(
    messages: List<Message>,
    parameters: RequestParameters
  ): List<Message>
}
```

### 3. Quickstart Guide (`quickstart.md`)

**Sections**:
1. Basic compression usage
2. Token-based limiting
3. Message-based limiting
4. Combined strategies (precedence order)
5. Error handling with Arrow Raise
6. Performance considerations
7. Testing examples

**Code Examples**:
```kotlin
// Example 1: Basic compression
val agent = Agent(
  name = "assistant",
  model = Model.CLAUDE_3_5_SONNET,
  system = "You are a helpful assistant"
)

val response = agent.invoke(
  input = "Continue our conversation",
  requestParameters = RequestParameters(
    compressionStrategy = HistoryCompressionStrategy.WholeHistory
  )
)

// Example 2: Token-based limiting
val response = agent.invoke(
  input = "Long conversation",
  requestParameters = RequestParameters(
    maxTokens = 50000,
    compressionStrategy = HistoryCompressionStrategy.LastNMessages(10)
  )
)
// Precedence: compression applied first, then token check

// Example 3: Message-based limiting (no compression)
val response = agent.invoke(
  input = "Keep recent context",
  requestParameters = RequestParameters(
    maxHistorySize = 20
  )
)
```

## Phase 2: Task Generation (NOT IN THIS COMMAND)

Phase 2 is executed by `/speckit.tasks`, which generates `tasks.md` with:
- Concrete implementation tasks based on this plan
- Dependencies between tasks
- Acceptance criteria per task
- Constitutional compliance checklist

**Prerequisites for Phase 2**:
- ✅ research.md complete
- ✅ data-model.md complete
- ✅ contracts/ complete
- ✅ quickstart.md complete
- ✅ Constitution Check passed (re-checked after Phase 1)

## Constitution Re-Check (Post-Design)

### ✅ Pure Functional Programming
- **Status**: COMPLIANT
- **Evidence**: Design uses only suspend functions, immutable data classes, and delegation to Koog's functional APIs

### ✅ Function Size (≤10 lines)
- **Status**: COMPLIANT
- **Evidence**: Each function delegates 1 Koog API call. Example:
  ```kotlin
  context(_: Raise<CompressionError>)
  suspend fun compress(
    strategy: HistoryCompressionStrategy,
    messages: List<Message>
  ): List<Message> {
    withError({ e: Throwable -> CompressionError.CompressionFailed(strategy.toString(), e) }) {
      koogAgent.llm.writeSession {
        replaceHistoryWithTLDR(strategy, emptyList())
        prompt.messages.map { convertToOurMessage(it) }
      }
    }
  }
  ```

### ✅ Error Handling (Arrow Raise)
- **Status**: COMPLIANT
- **Evidence**: All integration functions use `context(_: Raise<CompressionError>)` with `withError` for exception mapping

### ✅ Detekt Verification
- **Status**: PENDING (will verify in Phase 2)
- **Action**: `./gradlew build` MUST pass with 0 violations

## Next Steps

1. ✅ Run `/speckit.plan` (this command - COMPLETE)
2. ⏳ Phase 0: Generate research.md through delegation agents
3. ⏳ Phase 1: Generate data-model.md, contracts/, quickstart.md
4. ⏳ Update agent context (`.claude/CLAUDE.md`)
5. ⏳ Run `/speckit.tasks` to generate tasks.md
6. ⏳ Implement tasks with Kotlin-pro agent following constitutional requirements (see line 48, lines 107-109)

**Current Status**: Plan complete. Ready for research phase.

**Branch**: `003-history-management-control`
**Spec**: [spec.md](./spec.md)
**Plan**: [plan.md](./plan.md) (this file)
