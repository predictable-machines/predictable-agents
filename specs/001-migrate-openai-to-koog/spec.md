# Feature Specification: Agent Provider Migration to Koog

**Feature Branch**: `001-migrate-openai-to-koog`
**Created**: 2025-10-16
**Status**: Completed
**Input**: User description: "koog-migration. We want to change the implementation of the OpenAI provider for the Koog agents library which already supports persistence and autmatic message history management. Location change: /Users/raulraja/predictable-machines/predictable-agents/agents/src/commonMain/kotlin/agent/providers/openai . All test must pass unaltered. The koog repo https://github.com/JetBrains/koog must be cloned and researched at each step to see what needs to be changed. Success is all current tests passing without any modifications and the openai-client = { module = "com.aallam.openai:openai-client", version.ref = "openai-client" } replaced completely for koog without any public API changes"

**Final Implementation**: Replaced OpenAIProvider with provider-agnostic AgentProvider that supports all Koog providers (OpenAI, Anthropic, Google, Ollama). Deleted custom persistence and history compression abstractions in favor of Koog's built-in features.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Seamless Library Replacement (Priority: P1)

As a developer maintaining the Predictable Agents library, I need the agent provider implementation to use Koog instead of the current openai-client library, so that I can leverage Koog's built-in persistence and automatic message history management capabilities while supporting all LLM providers (OpenAI, Anthropic, Google, Ollama) without breaking any existing functionality.

**Why this priority**: This is the core requirement - replacing the underlying library while maintaining backward compatibility. Without this, the feature cannot exist.

**Independent Test**: Can be fully tested by running the existing test suite without modifications. If all tests pass, the migration is successful and delivers the value of library replacement with enhanced capabilities.

**Acceptance Scenarios**:

1. **Given** an existing agent provider using openai-client library, **When** the provider is migrated to Koog, **Then** all public API methods remain unchanged and accessible
2. **Given** existing test cases for chat completion, **When** tests are executed against the Koog-based AgentProvider, **Then** all tests pass without modification
3. **Given** existing test cases for structured output, **When** tests are executed against the Koog-based AgentProvider, **Then** all tests pass without modification
4. **Given** existing test cases for streaming responses, **When** tests are executed against the Koog-based AgentProvider, **Then** all tests pass without modification
5. **Given** existing test cases for tool calling, **When** tests are executed against the Koog-based AgentProvider, **Then** all tests pass without modification

---

### User Story 2 - Dependency Replacement (Priority: P2)

As a developer managing project dependencies, I need the openai-client dependency completely removed from the gradle configuration and replaced with Koog, so that the project has no lingering references to the old library.

**Why this priority**: This is essential cleanup that follows the implementation. It ensures no dependency conflicts or confusion about which library is in use.

**Independent Test**: Can be fully tested by examining gradle/libs.versions.toml file and verifying that openai-client references are removed and Koog dependencies are present. The project must build successfully without the old dependency.

**Acceptance Scenarios**:

1. **Given** the gradle dependency configuration, **When** searching for "com.aallam.openai:openai-client", **Then** no references are found in any gradle files
2. **Given** the gradle dependency configuration, **When** Koog dependencies are examined, **Then** all necessary Koog modules are properly declared with appropriate versions
3. **Given** the migrated codebase, **When** the project is built, **Then** the build completes successfully without errors related to missing dependencies
4. **Given** the migrated codebase, **When** import statements in provider files are examined, **Then** no imports reference the old openai-client package

---

### User Story 3 - Enhanced Capabilities Integration (Priority: P3)

As a developer using the agents library, I want to benefit from Koog's native persistence and message history management features, so that I have access to more robust state management capabilities in future enhancements.

**Why this priority**: This is a future enhancement opportunity enabled by the migration. While not immediately used, it provides value for future development. The migration should position the code to take advantage of these features when needed.

**Independent Test**: Can be tested by examining the Koog provider implementation to verify that it's structured in a way that could leverage Koog's persistence and history management features. No functional changes are required initially - just the architectural foundation.

**Acceptance Scenarios**:

1. **Given** the Koog-based provider implementation, **When** examining the code structure, **Then** the implementation is compatible with Koog's persistence mechanisms
2. **Given** the Koog-based provider implementation, **When** examining message handling, **Then** the code is structured to potentially use Koog's automatic message history management
3. **Given** the Koog library documentation, **When** comparing with the provider implementation, **Then** no architectural barriers exist to adopting Koog's advanced features in future iterations

---

### Edge Cases

- What happens when the Koog library has different API signatures than openai-client for equivalent functionality?
- How does the system handle cases where Koog's behavior differs slightly from openai-client in edge cases (e.g., error handling, retry logic)?
- What happens if Koog doesn't provide exact equivalents for all openai-client features currently used?
- How does the migration handle streaming response differences between the two libraries?
- What happens if Koog's model ID or configuration format differs from openai-client?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST maintain all existing public API methods of the Agent class without any signature changes
- **FR-002**: System MUST replace all internal usage of openai-client library classes with equivalent Koog library classes
- **FR-003**: System MUST pass all existing unit tests without any test modifications
- **FR-004**: System MUST successfully execute chat completion requests using Koog's LLMClient API
- **FR-005**: System MUST successfully execute structured output requests using Koog's API
- **FR-006**: System MUST successfully handle streaming responses using Koog's streaming API
- **FR-007**: System MUST correctly process tool calls and tool callbacks using Koog's mechanisms
- **FR-008**: System MUST support all Koog providers: OpenAI, Anthropic, Google, and Ollama
- **FR-009**: System MUST infer provider from model name using Koog's LLModel infrastructure
- **FR-010**: System MUST handle authentication using Koog's authentication mechanisms (API key and optional base URL)
- **FR-011**: System MUST completely remove the openai-client dependency from gradle/libs.versions.toml
- **FR-012**: System MUST add appropriate Koog dependencies to gradle/libs.versions.toml
- **FR-013**: System MUST update all import statements to reference Koog packages instead of openai-client packages
- **FR-014**: System MUST maintain compatibility with OpenAI-compatible APIs (like OpenRouter, local models) through Koog's OpenAI client
- **FR-015**: System MUST use Koog's built-in persistence and history management instead of custom abstractions

### Key Entities

- **AgentProvider**: The provider-agnostic class that orchestrates AI interactions using Koog's LLMClient interface. Supports all Koog providers (OpenAI, Anthropic, Google, Ollama)
- **ModelProvider**: Maps model names to Koog's LLModel instances, using predefined constants or creating custom models
- **ClientFactory**: Creates appropriate LLMClient instances based on API URL patterns
- **Message**: Conversation messages (user, assistant, system, tool) that must be converted to Koog's Prompt format
- **Model**: AI model configuration that maps to Koog's LLModel specification
- **AgentResponse**: Response objects (Text, Structured, StringStream, StructuredStream) that encapsulate AI outputs
- **Tool Callbacks**: Mechanisms for executing and tracking tool invocations during AI interactions
- **Request Parameters**: Configuration for AI requests (temperature, max tokens, etc.) that map to Koog's parameter system

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: All existing unit tests (AgentTest, AgentStructuredOutputTest, AgentToolsTest, etc.) pass without any modifications to test code ✅ COMPLETED
- **SC-002**: The gradle build completes successfully with zero references to "com.aallam.openai:openai-client" in dependency files ✅ COMPLETED
- **SC-003**: All existing provider functionality (chat completion, structured output, streaming, tool calling) produces equivalent results before and after migration ✅ COMPLETED
- **SC-004**: Zero changes required to public API methods of Agent class (method signatures, parameter types, return types remain identical) ✅ COMPLETED
- **SC-005**: The codebase contains zero import statements referencing "com.aallam.openai" package after migration ✅ COMPLETED
- **SC-006**: AgentProvider supports all Koog providers (OpenAI, Anthropic, Google, Ollama) with provider inference from model names ✅ COMPLETED
- **SC-007**: Custom persistence and history compression abstractions removed in favor of Koog's built-in features ✅ COMPLETED

## Assumptions

- Koog library provides equivalent functionality to openai-client for chat completions, structured outputs, and streaming
- Koog library supports OpenAI-compatible APIs with custom base URLs
- Koog library has similar error handling mechanisms that can be adapted to match current behavior
- The Koog repository at https://github.com/JetBrains/koog contains sufficient documentation and examples to guide the migration
- Koog's message format can be converted from the current Message types without data loss
- Koog supports tool calling functionality required by the agents library
- The migration can be completed without requiring changes to the broader agents library architecture

## Out of Scope

- Implementing new features that leverage Koog's persistence capabilities (this migration focuses on maintaining existing functionality)
- Performance optimization or improvements beyond maintaining current performance levels
- Changes to the Agent class or other parts of the library beyond the OpenAI provider implementation
- Migration of other providers (if any exist) to different underlying libraries
- Changes to the test suite or test assertions
- Documentation updates (assumed to be handled separately)
- Deprecation warnings or migration guides for library users (no public API changes means no user impact)

## Dependencies

- Access to the Koog repository at https://github.com/JetBrains/koog for understanding library capabilities
- Ability to clone and analyze the Koog repository structure and API design
- Understanding of Koog's authentication, request building, and response handling mechanisms
- Knowledge of Koog's streaming implementation and how it compares to openai-client
- Identification of appropriate Koog dependency coordinates for gradle configuration
