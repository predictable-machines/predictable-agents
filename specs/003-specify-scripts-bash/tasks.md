# Implementation Tasks: Developer-Controlled History Management

**Feature**: Developer-Controlled History Management
**Branch**: `003-history-management-control`
**Spec**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md)

## Implementation Strategy

**MVP Approach**: User Story 1 (P1) delivers the core value - explicit compression control. This is independently testable and deployable.

**Incremental Delivery**:
1. **MVP (User Story 1)**: Basic explicit compression - developers control when/how compression happens
2. **Enhancement 1 (User Story 4)**: Add remaining compression strategies (P2)
3. **Enhancement 2 (User Story 2)**: Add maxHistorySize parameter (P2)
4. **Enhancement 3 (User Story 3)**: Add maxTokens parameter and token estimation (P3)

**Parallel Execution Opportunities**: Identified with [P] marker throughout

---

## Phase 1: Setup & Dependencies

**Goal**: Prepare project structure and dependencies for Koog integration

- [X] T001 Research Koog AIAgent construction patterns in /tmp/koog/agents/agents-core/src/commonMain/kotlin/ai/koog/agents/core/agent/AIAgent.kt
- [X] T002 [P] Research Koog compression strategies in /tmp/koog/agents/agents-core/src/commonMain/kotlin/ai/koog/agents/core/dsl/extension/HistoryCompressionStrategies.kt
- [X] T003 [P] Research Koog message types in /tmp/koog/prompt/prompt-model/src/commonMain/kotlin/ai/koog/prompt/message/Message.kt
- [X] T004 [P] Research Koog session lifecycle in /tmp/koog/agents/agents-core/src/commonMain/kotlin/ai/koog/agents/core/agent/session/AIAgentLLMSession.kt
- [X] T005 [P] Research Koog tokenization in /tmp/koog/prompt/prompt-tokenizer/src/commonMain/kotlin/ai/koog/prompt/tokenizer/PromptTokenizer.kt
- [X] T006 Add Koog dependencies to agents/build.gradle.kts (ai.koog.agents.core, ai.koog.prompt) - Already present
- [X] T007 Create agents/src/commonMain/kotlin/predictable/agent/compression/ directory
- [X] T008 Create agents/src/commonTest/kotlin/agent/compression/ directory

---

## Phase 2: Foundational Infrastructure

**Goal**: Core types and adapters that ALL user stories depend on

**BLOCKING**: These tasks must complete before any user story can proceed

### Type System & Errors

- [X] T009 Create CompressionError sealed class in agents/src/commonMain/kotlin/predictable/agent/compression/CompressionError.kt with Arrow Raise context
- [X] T010 [P] Create MessageConverter for bidirectional Message ↔ Koog Message conversion in agents/src/commonMain/kotlin/predictable/agent/providers/MessageConverter.kt (Already exists in AgentProvider.kt)

### Public API Types

- [X] T011a Create CompressionStrategy sealed class in agents/src/commonMain/kotlin/agent/CompressionStrategy.kt (our public API type, not exposing Koog types)
- [X] T011b Create CompressionConverter in agents/src/commonMain/kotlin/agent/providers/CompressionConverter.kt (internal converter to Koog types)
- [X] T011c Extend RequestParameters data class in agents/src/commonMain/kotlin/predictable/agent/RequestParameters.kt with compressionStrategy, maxHistorySize, maxTokens, enableTokenCaching fields

### Koog Integration Foundation

- [X] T012 ~~Create KoogAgentFactory~~ **ARCHITECTURAL DECISION**: AgentProvider already handles Koog integration. Compression will be added directly to AgentProvider instead of creating a parallel wrapper system.
- [X] T013 Add compression method to AgentProvider in agents/src/commonMain/kotlin/agent/providers/AgentProvider.kt to apply compression via Koog's AIAgentLLMContext before prompt execution (Added `compressHistory()` with 4 helper functions, all ≤10 lines, using Arrow Raise context)

---

## Phase 3: User Story 1 - Basic Explicit History Compression (P1) 🎯 MVP

**Goal**: Enable developers to compress conversation history explicitly via `RequestParameters.compressionStrategy`

**Why Independent**: Core compression functionality. Once this works, agent can compress history on demand.

**Independent Test**: Create agent with 50+ messages, specify `compressionStrategy = WholeHistory`, verify history reduced to TL;DR.

**Acceptance Criteria**:
- ✅ Agent with compressionStrategy specified → history compressed
- ✅ Agent without compressionStrategy → no compression (full history maintained)
- ✅ Compression works with `invoke()`, `chat()`, `stream()`, `generateObject()` methods
- ✅ Zero Detekt violations

### Implementation Tasks

- [X] T014 ~~Implement KoogCompressionAdapter~~ **ARCHITECTURAL DECISION**: Compression integrated directly into AgentProvider.compressHistory() (lines 556-602)
- [X] T015 [US1] Create HistoryManager interface in agents/src/commonMain/kotlin/predictable/agent/compression/HistoryManager.kt with precedence order logic
- [X] T016 [US1] Implement HistoryManager in agents/src/commonMain/kotlin/predictable/agent/compression/HistoryManagerImpl.kt applying compression → tokens → size precedence
- [X] T017a-c [US1] ~~Wrap Koog AIAgent~~ **INTEGRATION**: Added historyManager property to AbstractAgent, integrated into messages() method (line 71-73, 424-428)
- [X] T018 [US1] Update AbstractAgent.messages() method to apply HistoryManager before model calls - Made suspend, applies history management via requiresHistoryManagement() check
- [X] T019 [US1] Add error handling with Arrow Raise context - Uses either{} for typed error handling, graceful fallback on failure (lines 397-405)
- [X] T019a [US1] Add warning logging when compression results in < 2 messages - Implemented in logCompressionResult() (lines 410-414)

### Testing Tasks

- [ ] T020 [P] [US1] Create CompressionIntegrationTest in agents/src/commonTest/kotlin/agent/compression/CompressionIntegrationTest.kt testing WholeHistory strategy
- [ ] T021 [P] [US1] Test compression with invoke() method verifies history reduced
- [ ] T022 [P] [US1] Test compression with chat() method verifies history reduced
- [ ] T023 [P] [US1] Test compression with stream() method verifies history reduced
- [ ] T024 [P] [US1] Test no compression when compressionStrategy=null verifies full history maintained

### Verification & Polish

- [ ] T025 [US1] Run ./gradlew build and fix ALL Detekt violations
- [ ] T026 [US1] Verify all 3 acceptance scenarios from spec pass
- [ ] T027 [US1] Document basic compression usage in quickstart examples

**US1 Deliverable**: Developer can explicitly compress history via RequestParameters. Independently deployable MVP. ✅

---

## Phase 4: User Story 4 - Compression Strategy Selection (P2)

**Goal**: Support all 5 Koog compression strategies (WholeHistory, LastNMessages, Chunked, FromTimestamp, WholeHistoryMultipleSystemMessages)

**Why Independent**: Builds on US1's compression foundation. Tests different compression algorithms.

**Independent Test**: Apply each strategy variant, verify expected message transformation pattern.

**Acceptance Criteria**:
- ✅ LastNMessages(n) keeps last N messages, compresses rest
- ✅ Chunked(size) compresses in groups
- ✅ FromTimestamp(ts) compresses messages before timestamp
- ✅ WholeHistoryMultipleSystemMessages handles multiple system messages
- ✅ All strategies work with all AbstractAgent methods

### Implementation Tasks

- [ ] T028 [P] [US4] Research Koog's LastNMessages strategy in /tmp/koog/agents/agents-core/src/commonMain/kotlin/ai/koog/agents/core/dsl/extension/HistoryCompressionStrategies.kt
- [ ] T029 [P] [US4] Research Koog's Chunked strategy implementation patterns
- [ ] T030 [P] [US4] Research Koog's FromTimestamp strategy implementation patterns
- [ ] T031 [P] [US4] Research Koog's WholeHistoryMultipleSystemMessages strategy implementation patterns
- [ ] T032 [US4] Add support for LastNMessages(n) to KoogCompressionAdapter
- [ ] T033 [P] [US4] Add support for Chunked(size) to KoogCompressionAdapter
- [ ] T034 [P] [US4] Add support for FromTimestamp(ts) to KoogCompressionAdapter
- [ ] T035 [P] [US4] Add support for WholeHistoryMultipleSystemMessages to KoogCompressionAdapter

### Testing Tasks

- [ ] T036 [P] [US4] Test LastNMessages(10) with 50-message conversation in CompressionIntegrationTest
- [ ] T037 [P] [US4] Test Chunked(5) verifies messages compressed in groups of 5
- [ ] T038 [P] [US4] Test FromTimestamp verifies messages before timestamp compressed
- [ ] T039 [P] [US4] Test WholeHistoryMultipleSystemMessages with conversations containing multiple system messages
- [ ] T040 [P] [US4] Create CompressionPropertiesTest in agents/src/commonTest/kotlin/agent/compression/CompressionPropertiesTest.kt with property-based tests verifying messagesBefore > messagesAfter

### Verification & Polish

- [ ] T041 [US4] Run ./gradlew build and fix ALL Detekt violations
- [ ] T042 [US4] Verify all 3 acceptance scenarios from spec pass
- [ ] T043 [US4] Document all compression strategies in quickstart with examples

**US4 Deliverable**: Developers can choose from 5 compression strategies. Independently testable. ✅

---

## Phase 5: User Story 2 - History Size Control (P2)

**Goal**: Support `maxHistorySize` parameter to limit message count

**Why Independent**: Separate concern from compression. Can test by setting maxHistorySize without compression.

**Independent Test**: Set `maxHistorySize=20`, run conversation with 100 messages, verify only 20 most recent sent to model.

**Acceptance Criteria**:
- ✅ maxHistorySize=N keeps only last N messages
- ✅ Messages trimmed before each API call
- ✅ Compression takes precedence when both specified (per precedence order)
- ✅ Works with all AbstractAgent methods

### Implementation Tasks

- [ ] T044 [US2] Add maxHistorySize handling to HistoryManager.apply() method
- [ ] T045 [US2] Implement message trimming logic preserving most recent messages
- [ ] T046 [US2] Verify precedence order: compressionStrategy → maxTokens → maxHistorySize
- [ ] T047 [US2] Add validation ensuring system prompt never trimmed

### Testing Tasks

- [ ] T048 [P] [US2] Test maxHistorySize=20 with 100 messages verifies exactly 20 messages sent
- [ ] T049 [P] [US2] Test maxHistorySize with compression verifies compression takes precedence
- [ ] T050 [P] [US2] Test maxHistorySize=30 across multiple turns verifies limit maintained
- [ ] T051 [P] [US2] Create HistoryManagerTest in agents/src/commonTest/kotlin/agent/compression/HistoryManagerTest.kt testing precedence order

### Verification & Polish

- [ ] T052 [US2] Run ./gradlew build and fix ALL Detekt violations
- [ ] T053 [US2] Verify all 3 acceptance scenarios from spec pass
- [ ] T054 [US2] Document maxHistorySize parameter usage in quickstart

**US2 Deliverable**: Developers can limit history by message count. Independently testable. ✅

---

## Phase 6: User Story 3 - Token-Based History Control (P3)

**Goal**: Support `maxTokens` parameter with Koog's PromptTokenizer integration

**Why Independent**: Advanced token management. Requires token estimation but independent of other features.

**Independent Test**: Set `maxTokens=50000`, create conversation with 100k tokens, verify trimmed to ≤50k.

**Acceptance Criteria**:
- ✅ maxTokens enforced before API call
- ✅ Token estimation accuracy within 10% (FR-009, SC-004)
- ✅ Compression applied first, then token check (precedence order)
- ✅ Clear error if maxTokens < system prompt tokens (FR-016)

### Implementation Tasks

- [ ] T055 [US3] Research Koog's PromptTokenizer usage patterns in /tmp/koog/prompt/prompt-tokenizer/
- [ ] T056 [US3] Integrate Koog's CachingTokenizer in HistoryManager for token estimation
- [ ] T057 [US3] Add maxTokens handling to HistoryManager.apply() method
- [ ] T058 [US3] Implement token-based trimming logic removing oldest messages first
- [ ] T059 [US3] Add validation raising error if maxTokens < system prompt token count
- [ ] T060 [US3] Verify precedence order: compression → maxTokens → maxHistorySize

### Testing Tasks

- [ ] T061 [P] [US3] Test maxTokens=50000 with 100k token conversation verifies tokens ≤50k
- [ ] T062 [P] [US3] Test token estimation accuracy against OpenAI/Anthropic counters (within 10%)
- [ ] T063 [P] [US3] Test maxTokens with compression verifies compression applied first
- [ ] T064 [P] [US3] Test maxTokens < system prompt tokens raises clear error
- [ ] T065 [P] [US3] Add token estimation tests to CompressionPropertiesTest verifying tokensBefore > tokensAfter

### Verification & Polish

- [ ] T066 [US3] Run ./gradlew build and fix ALL Detekt violations
- [ ] T067 [US3] Verify all 3 acceptance scenarios from spec pass
- [ ] T068 [US3] Document maxTokens parameter and token estimation in quickstart

**US3 Deliverable**: Developers can limit history by token count with accurate estimation. Independently testable. ✅

---

## Phase 7: Cross-Cutting Concerns & Polish

**Goal**: Final integration, documentation, and quality assurance

### Integration Testing

- [ ] T069 [P] Create KoogIntegrationTest in agents/src/commonTest/kotlin/agent/KoogIntegrationTest.kt testing end-to-end scenarios
- [ ] T070 [P] Test all 4 user stories working together with combined parameters
- [ ] T071 [P] Test edge case: compression with small history (3 messages) verifies no compression applied
- [ ] T072 [P] Test edge case: multiple parameters verifies precedence order maintained

### Documentation

- [ ] T073 Create comprehensive quickstart.md in specs/003-specify-scripts-bash/quickstart.md with all usage examples
- [ ] T074 [P] Document error handling patterns with Arrow Raise context
- [ ] T075 [P] Document performance considerations and benchmarks
- [ ] T076 [P] Add troubleshooting section for common issues

### Final Verification

- [ ] T077 Run ./gradlew build from repository root and fix ALL Detekt violations (constitutional requirement)
- [ ] T078 Verify all 7 success criteria (SC-001 through SC-007) pass
- [ ] T079 Verify zero automatic compression triggers (FR-002, SC-006)
- [ ] T080 Run full test suite and ensure 100% pass rate

---

## Task Dependencies

### Sequential Dependencies (Must Complete in Order)

**Phase 1 → Phase 2 → Phase 3+**
- Phase 1 (Setup) must complete before Phase 2
- Phase 2 (Foundational) must complete before ANY user story
- User Stories (Phase 3-6) are independent of each other

### User Story Completion Order (By Priority)

1. **Phase 3 (US1 - P1)**: MVP - Core compression functionality
2. **Phase 4 (US4 - P2)** OR **Phase 5 (US2 - P2)**: Either can be next (same priority)
3. **Phase 6 (US3 - P3)**: Advanced token management

### Within-Phase Dependencies

**Phase 2 (Foundational)**:
- T009 (CompressionError) → blocks all compression tasks
- T010 (MessageConverter) → blocks T012 (KoogAgentFactory)
- T011 (RequestParameters) → blocks T015 (HistoryManager interface)
- T012 (KoogAgentFactory) → blocks T017 (AbstractAgent refactor)
- T013 (KoogCompressionAdapter interface) → blocks T014 (implementation)

**Phase 3 (US1)**:
- T014 (KoogCompressionAdapter impl) → blocks T016 (HistoryManager impl)
- T016 (HistoryManager impl) → blocks T017 (AbstractAgent refactor)
- T017 (AbstractAgent refactor) → blocks T018 (messages() update)
- T018 (messages() update) → blocks testing tasks T020-T024

**Phase 4 (US4)**:
- T028-T031 (Research) can run in parallel
- T032-T035 (Strategy implementations) can run in parallel AFTER research
- Testing T036-T040 can run in parallel AFTER implementations

**Phase 5 (US2)**:
- T044 → T045 → T046 → T047 (sequential implementation)
- Testing T048-T051 can run in parallel AFTER T047

**Phase 6 (US3)**:
- T055 (Research) → T056 (Tokenizer integration) → T057-T060 (sequential)
- Testing T061-T065 can run in parallel AFTER T060

---

## Parallel Execution Examples

### Phase 1 (Setup) - Maximum Parallelism
```bash
# All research tasks can run concurrently
parallel:
  - T002: Research compression strategies
  - T003: Research message types
  - T004: Research session lifecycle
  - T005: Research tokenization
```

### Phase 2 (Foundational) - Type System in Parallel
```bash
# After T011 completes, these can run in parallel
parallel:
  - T009: CompressionError
  - T010: MessageConverter
```

### Phase 3 (US1) - Testing in Parallel
```bash
# After T019 completes, all tests can run concurrently
parallel:
  - T020: Test WholeHistory strategy
  - T021: Test with invoke()
  - T022: Test with chat()
  - T023: Test with stream()
  - T024: Test no compression
```

### Phase 4 (US4) - Strategy Research in Parallel
```bash
# All strategy research can run concurrently
parallel:
  - T028: Research LastNMessages
  - T029: Research Chunked
  - T030: Research FromTimestamp
  - T031: Research WholeHistoryMultipleSystemMessages
```

### Phase 4 (US4) - Strategy Implementation in Parallel
```bash
# After research, all implementations can run concurrently
parallel:
  - T033: Implement Chunked
  - T034: Implement FromTimestamp
  - T035: Implement WholeHistoryMultipleSystemMessages
```

### Phase 4 (US4) - Strategy Testing in Parallel
```bash
# All strategy tests can run concurrently
parallel:
  - T036: Test LastNMessages
  - T037: Test Chunked
  - T038: Test FromTimestamp
  - T039: Test WholeHistoryMultipleSystemMessages
  - T040: Property-based tests
```

### Phase 5 (US2) - Testing in Parallel
```bash
# All US2 tests can run concurrently
parallel:
  - T048: Test maxHistorySize=20
  - T049: Test with compression
  - T050: Test across multiple turns
  - T051: Test precedence order
```

### Phase 6 (US3) - Testing in Parallel
```bash
# All US3 tests can run concurrently
parallel:
  - T061: Test maxTokens=50000
  - T062: Test estimation accuracy
  - T063: Test with compression
  - T064: Test error cases
  - T065: Property-based token tests
```

### Phase 7 (Polish) - Documentation in Parallel
```bash
# All documentation tasks can run concurrently
parallel:
  - T074: Error handling docs
  - T075: Performance docs
  - T076: Troubleshooting docs
```

---

## Summary

**Total Tasks**: 80
**MVP Tasks (US1)**: 27 (T001-T027)
**Enhancement Tasks**: 53 (T028-T080)

**Task Breakdown by Phase**:
- Phase 1 (Setup): 8 tasks
- Phase 2 (Foundational): 5 tasks (BLOCKING)
- Phase 3 (US1 - MVP): 14 tasks
- Phase 4 (US4 - P2): 16 tasks
- Phase 5 (US2 - P2): 11 tasks
- Phase 6 (US3 - P3): 14 tasks
- Phase 7 (Polish): 12 tasks

**Parallel Opportunities**: 45 tasks marked with [P] can run concurrently with other tasks

**Independent Test Criteria Per Story**:
- **US1**: Create agent, compress with WholeHistory, verify history reduced
- **US4**: Apply each strategy, verify correct transformation
- **US2**: Set maxHistorySize=20, verify ≤20 messages
- **US3**: Set maxTokens=50000, verify ≤50k tokens

**Suggested MVP Scope**: Phase 1 + Phase 2 + Phase 3 (US1) = 27 tasks

**Constitutional Compliance**:
- ✅ Every task requires Koog research before implementation
- ✅ Arrow Raise context for all error handling
- ✅ Functions ≤10 lines through delegation
- ✅ Property-based testing for compression properties
- ✅ Detekt verification after EVERY user story phase

---

## Task Execution Checklist

When implementing tasks, ensure:

- [ ] Read Koog research in /tmp/koog BEFORE implementing (constitutional requirement)
- [ ] Use Arrow Raise context (`context(_: Raise<CompressionError>)`) for error handling
- [ ] Keep functions ≤10 lines (delegation pattern)
- [ ] Run `./gradlew build` after each phase and fix ALL Detekt violations
- [ ] Verify acceptance criteria for each user story
- [ ] Update quickstart.md with usage examples
- [ ] Test with all AbstractAgent methods (invoke, chat, stream, generateObject)

**Ready for implementation with kotlin-pro agent!** 🚀
