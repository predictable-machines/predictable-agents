# Feature Specification: Developer-Controlled History Management

**Feature Branch**: `003-history-management-control`
**Created**: 2025-10-16
**Status**: Draft
**Input**: Developer-controlled history management through RequestParameters aligned with Koog architecture

## Research Foundation

This specification is based on comprehensive research of the Koog repository at `/tmp/koog`. Key findings:

### Koog's Architecture Principles

1. **Message History**: Lives in `Prompt.messages`, managed by `AIAgentLLMContext`
2. **Compression**: EXPLICIT, not automatic - developers call `nodeLLMCompressHistory()` or `replaceHistoryWithTLDR()`
3. **Persistence**: Checkpoints save current message state (compressed or not)
4. **Separation of Concerns**: History management, compression, and persistence are independent

### Koog's Compression Strategies

- `HistoryCompressionStrategy.WholeHistory` - Compress entire history into single TL;DR
- `HistoryCompressionStrategy.LastNMessages(n)` - Keep last N messages, compress rest
- `HistoryCompressionStrategy.Chunked(chunkSize)` - Compress in chunks
- `HistoryCompressionStrategy.FromTimestamp(timestamp)` - Compress before timestamp
- `HistoryCompressionStrategy.WholeHistoryMultipleSystemMessages` - Compress with multiple system prompts

**Key Pattern**: Compression modifies `Prompt.messages` in-place within a `writeSession`, replacing message sequences with TL;DR summaries.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Basic Explicit History Compression (Priority: P1)

A developer using the agent wants to compress conversation history explicitly when it exceeds their token budget, without automatic intervention. They need to control when compression happens and which strategy is used.

**Why this priority**: Core functionality that aligns with Koog's architecture. This is the foundation - explicit control rather than automatic compression.

**Independent Test**: Can be fully tested by creating an agent, running a multi-turn conversation, then calling compression explicitly via RequestParameters and verifying history is reduced.

**Acceptance Scenarios**:

1. **Given** an agent with a long conversation history (50+ messages)
   **When** developer specifies `RequestParameters(compressionStrategy = CompressionStrategy.WholeHistory)` on next invocation
   **Then** history is compressed to a TL;DR summary and the agent continues with compressed context

2. **Given** an agent with history consuming 8000 tokens
   **When** developer specifies `RequestParameters(compressionStrategy = CompressionStrategy.LastNMessages(10))`
   **Then** only last 10 messages remain, earlier messages are compressed to TL;DR, and token count is reduced

3. **Given** an agent without compression strategy specified
   **When** developer makes a call with default RequestParameters
   **Then** NO compression occurs, full history is maintained

---

### User Story 2 - History Size Control (Priority: P2)

A developer wants to limit conversation history size to prevent context window overflow, but needs to retain recent messages for continuity. They want to specify a maximum message count and have older messages automatically removed.

**Why this priority**: Practical necessity for production usage. Once explicit compression exists (P1), developers need convenient size management.

**Independent Test**: Can be tested by setting `maxHistorySize` in RequestParameters and verifying message list never exceeds limit.

**Acceptance Scenarios**:

1. **Given** an agent with 100 messages in history
   **When** developer specifies `RequestParameters(maxHistorySize = 20)`
   **Then** only the most recent 20 messages are sent to the model, older messages are discarded

2. **Given** an agent with `maxHistorySize = 30` configured
   **When** conversation grows beyond 30 messages
   **Then** oldest messages are automatically dropped before each API call to maintain the limit

3. **Given** an agent with both `maxHistorySize = 20` and `compressionStrategy = WholeHistory`
   **When** history exceeds 20 messages
   **Then** compression takes precedence - entire history is compressed to TL;DR instead of truncation

---

### User Story 3 - Token-Based History Control (Priority: P3)

A developer wants to manage history based on estimated token count rather than message count, ensuring they never exceed model context windows (e.g., 128k tokens for Claude).

**Why this priority**: Advanced feature for token budget management. Builds on P1/P2, needed for cost optimization.

**Independent Test**: Can be tested by setting `maxTokens` and verifying total prompt tokens stay below limit.

**Acceptance Scenarios**:

1. **Given** an agent with history consuming 100k tokens
   **When** developer specifies `RequestParameters(maxTokens = 50000)`
   **Then** messages are trimmed from the start until estimated tokens <= 50k

2. **Given** an agent with `maxTokens = 80000` and `compressionStrategy = Chunked(10)`
   **When** token estimate exceeds limit
   **Then** chunked compression is applied to reduce token count before API call

3. **Given** an agent approaching context window limit
   **When** developer specifies both `maxTokens` and `compressionStrategy`
   **Then** compression is attempted first; if still over limit, messages are trimmed

---

### User Story 4 - Compression Strategy Selection (Priority: P2)

A developer wants to choose different compression strategies based on conversation type. For customer support, they want to keep recent messages. For document analysis, they want to compress by timestamp sections.

**Why this priority**: Flexibility is essential once basic compression exists. Different use cases need different strategies.

**Independent Test**: Can be tested by applying each strategy variant and verifying the expected message transformation.

**Acceptance Scenarios**:

1. **Given** a 50-message conversation
   **When** `RequestParameters(compressionStrategy = LastNMessages(10))`
   **Then** last 10 messages remain intact, messages 1-40 become single TL;DR summary

2. **Given** a conversation spanning 2 hours
   **When** `RequestParameters(compressionStrategy = FromTimestamp(1hour ago))`
   **Then** messages before timestamp are compressed, recent hour remains intact

3. **Given** a technical analysis conversation
   **When** `RequestParameters(compressionStrategy = Chunked(chunkSize = 5))`
   **Then** messages are compressed in groups of 5, maintaining logical boundaries

---

### Edge Cases

- What happens when **compression strategy is specified but history is already small** (e.g., 3 messages)?
  - Should NOT compress - minimum threshold required (e.g., 10 messages)

- How does system handle **compression reducing history below minimum** (e.g., compress to 1 message when model needs at least 2)?
  - Should preserve minimum required messages, compress only excess

- What happens when **maxTokens is set lower than system prompt token count**?
  - Should raise clear error: "maxTokens must accommodate system prompt"

- How does system handle **multiple parameters** (e.g., compressionStrategy, maxHistorySize, and maxTokens all specified)?
  - Apply in precedence order: compressionStrategy → maxTokens → maxHistorySize (compression first for quality, then token limit, then message limit)

- What happens when **compression is requested mid-streaming**?
  - **NOT A REAL ISSUE**: Research of Koog shows compression (`writeSession`) and streaming (`requestLLMStreaming()`) are separate. Compression happens BEFORE streaming, never during.

## Requirements *(mandatory)*

### Functional Requirements

#### Compression Control

- **FR-001**: System MUST support explicit compression via `RequestParameters.compressionStrategy`
- **FR-002**: System MUST NOT compress history automatically without explicit strategy specified
- **FR-003**: System MUST support all Koog compression strategies: WholeHistory, LastNMessages, Chunked, FromTimestamp, WholeHistoryMultipleSystemMessages
- **FR-004**: System MUST apply compression immediately before each model API call (not cached across multiple calls)
- **FR-005**: System MUST preserve system prompt during compression (never compress system message)

#### History Size Management

- **FR-006**: System MUST support `RequestParameters.maxHistorySize` to limit message count
- **FR-007**: System MUST trim oldest messages first when exceeding maxHistorySize
- **FR-008**: System MUST support `RequestParameters.maxTokens` to limit estimated token count
- **FR-009**: System MUST estimate tokens accurately enough to prevent context window overflow (within 10% of actual tokens reported by the model provider's token usage API)
- **FR-010**: System MUST prioritize compression over truncation when both specified

#### Integration Points

- **FR-011**: Compression MUST work with AbstractAgent's existing message handling in `messages(input: AgentInput)` (line 377)
- **FR-012**: History management MUST work with streaming (`stream()` methods)
- **FR-013**: History management MUST work with structured generation (`generateObject()`)
- **FR-014**: History management MUST work with text generation (`invoke()` methods)
- **FR-015**: Compression state MUST NOT be saved to checkpoints (checkpoints save current message state only)

#### Error Handling

- **FR-016**: System MUST raise clear error if maxTokens < system prompt token count
- **FR-017**: System MUST raise clear error if compression fails (e.g., serialization error)
- **FR-018**: System MUST NOT fail silently - all history management errors must surface to developer
- **FR-019**: System MUST log a warning (WARN level via kotlinx-logging) if compression results in < 2 messages (potential context loss). Warning MUST include compression strategy name and resulting message count. Operation MUST NOT be blocked - this is informational only.

### Key Entities *(include if feature involves data)*

**Koog Components (from wrapped AIAgent system)**:
- **AIAgent**: Koog's agent system providing compression, memory, and persistence features
- **AIAgentLLMContext**: Thread-safe context managing `Prompt.messages` with RWLock
- **AIAgentLLMWriteSession**: Session for modifying prompts and applying compression
- **HistoryCompressionStrategy**: Koog's sealed class with strategies (WholeHistory, LastNMessages, Chunked, FromTimestamp, WholeHistoryMultipleSystemMessages)
- **PromptTokenizer**: Koog's interface for token counting (CachingTokenizer, OnDemandTokenizer, TiktokenEncoder)

**Our API Layer (facades over Koog)**:
- **AbstractAgent**: Refactored to wrap Koog's AIAgent, provides our simplified API
- **RequestParameters**: Extended with Koog-mapped fields:
  - `compressionStrategy: HistoryCompressionStrategy?` - Maps to Koog's compression strategies
  - `maxHistorySize: Int?` - Handled by message filtering before compression
  - `maxTokens: Int?` - Validated using Koog's PromptTokenizer
  - `enableTokenCaching: Boolean = true` - Maps to Koog's CachingTokenizer vs OnDemandTokenizer
- **AgentProvider**: Refactored as adapter creating and configuring Koog AIAgents
- **Message**: Our message type with converters to/from Koog's `ai.koog.prompt.message.Message`

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Developer can compress 100-message conversation to <10 messages with explicit compression call, verifiable in tests
- **SC-002**: History management works correctly with all AbstractAgent methods: `invoke()`, `chat()`, `stream()`, `generateObject()`, `streamObject()`
- **SC-003**: Setting `maxHistorySize = 20` keeps message list at exactly 20 or fewer across multiple conversation turns
- **SC-004**: Token estimation accuracy within 10% of actual tokens (measured against OpenAI/Anthropic token counters)
- **SC-005**: Compression reduces token count by at least 60% for conversations with 50+ messages
- **SC-006**: Zero automatic compression triggers - compression only happens when explicitly requested via RequestParameters
- **SC-007**: All edge cases documented above handle gracefully without crashes

## Implementation Notes

### MANDATORY: Koog Research Before Each Task (NON-NEGOTIABLE)

**Constitutional Requirement**: Before implementing ANY task in this feature, developers MUST research Koog's actual implementation in `/tmp/koog` to:

1. **Verify how Koog handles** the specific functionality (compression, sessions, streaming, etc.)
2. **Identify integration points** and required adapters/converters
3. **Understand constraints** and lifecycle management
4. **Validate assumptions** about how features work

**Examples of Required Research**:
- Before implementing compression → Research `HistoryCompressionStrategies.kt` and `AIAgentLLMActions.kt`
- Before integrating sessions → Research `AIAgentLLMSession.kt` and `AIAgentLLMWriteSession`
- Before handling streaming → Research `executeStreaming()` and `StreamFrame`
- Before persistence integration → Research `AgentCheckpointData` and snapshot providers

**Rationale**: This spec was created AFTER deep Koog research that revealed architectural decisions (Option A: wrap AIAgent). Every task implementation MUST continue this pattern to prevent:
- Reimplementing what Koog already provides
- Missing Koog's constraints and patterns
- Creating architectural mismatches
- Invalid edge case handling

**Failure to research = Constitution violation**.

### Architecture: Wrapping Koog's AIAgent System

**Major Architectural Change**: AbstractAgent will wrap Koog's `AIAgent` system instead of directly using `LLMClient` through `AgentProvider`.

**Current Architecture** (being replaced):
```
AbstractAgent → AgentProvider → LLMClient.execute(prompt, model, tools)
```

**New Architecture** (Option A selected):
```
AbstractAgent → Koog AIAgent (with features) → LLMClient
                    ↓
        Compression, Memory, Persistence, etc.
```

### Benefits of Wrapping Koog's AIAgent

1. **Native Compression Support**: Direct access to all Koog's `HistoryCompressionStrategy` implementations
2. **Memory Management**: Access to Koog's memory features for conversation context
3. **Built-in Persistence**: Koog's checkpoint system already integrated
4. **Feature Extensibility**: Can expose other Koog features (tools, multi-agent, etc.) in future
5. **No Duplication**: Reuse Koog's battle-tested compression logic instead of reimplementing

### Integration Points

**Location**: `AbstractAgent.kt` - fundamental refactoring required

**Current AbstractAgent Structure**:
```kotlin
abstract class AbstractAgent(
  val name: String,
  val description: String,
  val system: String,
  val model: Model,
  // ...
) {
  private val provider: AgentProvider by lazy { ... }

  suspend fun invoke(input: String): String =
    provider.chatCompletion(model, messages, tools, parameters, toolCallBack).value
}
```

**New AbstractAgent Structure** (wrapping Koog):
```kotlin
abstract class AbstractAgent(
  val name: String,
  val description: String,
  val system: String,
  val model: Model,
  // ...
) {
  private val koogAgent: AIAgent by lazy {
    createKoogAgent(name, description, system, model, tools, apiKey)
  }

  suspend fun invoke(input: String, parameters: RequestParameters): String {
    // Apply compression if specified in parameters
    if (parameters.compressionStrategy != null) {
      koogAgent.llm.writeSession {
        replaceHistoryWithTLDR(parameters.compressionStrategy, memoryMessages = emptyList())
      }
    }

    // Execute using Koog's agent system
    return koogAgent.execute(input, convertParams(parameters))
  }
}
```

### Key Differences from Rolled-Back Implementation

The previous implementation had these issues (now corrected):

- ❌ Automatic compression based on thresholds → ✅ Explicit compression via RequestParameters
- ❌ Tracked compression events in checkpoints → ✅ No event tracking, checkpoints save current state
- ❌ Tied compression to persistence → ✅ Compression is independent, happens before API call
- ❌ Reimplemented compression logic → ✅ Wrap and delegate to Koog's native compression

### Migration Impact

This architectural change affects:

1. **AbstractAgent.kt**: Major refactoring to wrap AIAgent instead of using AgentProvider directly
2. **AgentProvider.kt**: May be deprecated or refactored as adapter between our API and Koog's
3. **RequestParameters**: New fields map to Koog's configuration
4. **Message types**: Need mapping between our `Message` and Koog's `Message` types
5. **Tool integration**: Tools need to work with Koog's `ToolDescriptor` (already done in AgentProvider)

### Backward Compatibility Strategy

To minimize breaking changes:

1. **Keep existing AbstractAgent API**: Public methods (`invoke()`, `chat()`, `stream()`, etc.) unchanged
2. **Internal delegation**: All methods delegate to wrapped Koog AIAgent
3. **Transparent compression**: Compression applied based on RequestParameters without API changes
4. **Gradual migration**: Existing code continues working, new compression features are opt-in

## Clarifications

### Session 2025-10-16

- Q: Since Koog's compression strategies require `AIAgentLLMWriteSession` but we only use `LLMClient`, how should we integrate Koog's compression features? → A: Wrap Koog's AIAgent system within AbstractAgent (full Koog integration with architectural change)
- Q: When a developer specifies multiple history management parameters (compressionStrategy, maxHistorySize, maxTokens), what order should they be applied in? → A: compressionStrategy → maxTokens → maxHistorySize (compression first for best quality, then token limits, then message count)
- Q: What happens when compression is requested mid-streaming? → A: NOT A REAL ISSUE - Research of /tmp/koog shows compression (writeSession) and streaming (requestLLMStreaming) are separate operations. Compression happens BEFORE streaming, never during

## Open Questions / Clarifications Needed

None - specification is based on thorough research of Koog's actual architecture and patterns.
