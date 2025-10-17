# Quickstart: Koog Integration

**Feature**: OpenAI Provider Migration to Koog
**Date**: 2025-10-16
**Audience**: Developers implementing or using the Koog-based OpenAIProvider

---

## Overview

This guide provides a quick reference for developers working with the Koog-integrated OpenAIProvider. The migration replaces the internal HTTP client (`com.aallam.openai:openai-client`) with JetBrains Koog (`ai.koog:koog-agents:0.5.0`) while maintaining 100% backward compatibility with the existing public API.

**Key Principle**: The OpenAIProvider public API remains unchanged. Internal implementation uses Koog's `OpenAILLMClient` with type adapters for seamless integration.

---

## For Implementation Developers

### Prerequisites

1. **Clone Koog Repository** (constitutional requirement):
   ```bash
   cd /tmp
   git clone https://github.com/JetBrains/koog.git
   ```

2. **Review Documentation**:
   - [Research findings](./research.md)
   - [Data model](./data-model.md)
   - [Type adapter contracts](./contracts/type-adapters.md)

3. **Use Specialized Agent**:
   - Agent: `.claude/agents/koog-integration-expert.md`
   - For all Koog-specific implementation tasks

### Implementation Workflow

#### Step 1: Update Dependencies

Edit `gradle/libs.versions.toml`:

```toml
[versions]
# REMOVE
# openai-client = "4.0.1"

# ADD
koog = "0.5.0"

# VERIFY (already correct)
kotlinx-coroutines = "1.10.2"  # ✅ Matches Koog requirement
kotlin-serialization = "1.9.0"  # ✅ Compatible with Koog 1.8.1

[libraries]
# REMOVE
# openai-client = { module = "com.aallam.openai:openai-client", version.ref = "openai-client" }

# ADD
koog-agents = { module = "ai.koog:koog-agents", version.ref = "koog" }
```

Edit `agents/build.gradle.kts`:

```kotlin
// REMOVE
// implementation(libs.openai.client)

// ADD
implementation(libs.koog.agents)
```

Run dependency sync:
```bash
./gradlew --stop  # Clear daemon cache
./gradlew build
```

---

#### Step 2: Create Type Adapter Module

Create `/agents/src/commonMain/kotlin/agent/providers/openai/adapters/`:

```
adapters/
├── MessageAdapters.kt       # Message type conversions
├── ModelAdapters.kt         # Model type conversions
├── ParameterAdapters.kt     # Request parameter conversions
├── ResponseAdapters.kt      # Response type conversions
├── ToolAdapters.kt          # Tool descriptor conversions
├── MetadataAdapters.kt      # Metadata conversions
└── ConversionErrors.kt      # Error types for adapters
```

**Constitutional Constraint**: Each adapter function ≤10 lines

**Example** (`MessageAdapters.kt`):
```kotlin
package predictable.agent.providers.openai.adapters

import arrow.core.raise.Raise
import arrow.core.raise.context.ensure
import predictable.agent.Message
import ai.koog.prompt.message.Message as KoogMessage

context(_: Raise<ConversionError>)
fun Message.System.toKoog(): KoogMessage.System {
    ensure(content.isNotBlank()) { ConversionError.EmptyContent }
    return KoogMessage.System(content)
}

fun KoogMessage.System.fromKoog(): Message.System =
    Message.System(content)
```

**Testing**: Each adapter MUST have:
- Success path test
- Error path test
- Immutability test

---

#### Step 3: Update OpenAIProvider Implementation

Modify `agents/src/commonMain/kotlin/agent/providers/openai/OpenAIProvider.kt`:

**Before (openai-client)**:
```kotlin
class OpenAIProvider(
    private val baseUrl: String?,
    private val apiKey: String,
) {
    private val openAI = OpenAI(
        host = baseUrl?.let { OpenAIHost(it) } ?: OpenAIHost.OpenAI,
        token = apiKey,
        logging = LoggingConfig(logLevel = LogLevel.None)
    )

    suspend fun chatCompletion(...): AgentResponse.Text {
        val request = buildChatCompletionRequest(...)
        val response = openAI.chatCompletion(request)
        // ...
    }
}
```

**After (Koog)**:
```kotlin
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import predictable.agent.providers.openai.adapters.*

class OpenAIProvider(
    private val baseUrl: String?,
    private val apiKey: String,
) {
    private val koogClient = OpenAILLMClient(
        apiKey = apiKey,
        settings = OpenAIClientSettings(
            baseUrl = baseUrl ?: "https://api.openai.com"
        )
    )

    context(_: Raise<ProviderError>)
    suspend fun chatCompletion(...): AgentResponse.Text {
        val prompt = buildPrompt(messages)  // Uses adapters
        val responses = executeKoog(prompt, model, tools)  // Wrapped in Raise
        return responses.toAgentResponseText(model.name).bind()
    }

    context(_: Raise<ProviderError>)
    private suspend fun executeKoog(...): List<KoogMessage.Response> =
        catch({ ProviderError.ExecutionFailed(it.message ?: "Koog error") }) {
            koogClient.execute(prompt, model, tools)
        }
}
```

**Key Changes**:
1. Replace `OpenAI` client with `OpenAILLMClient`
2. Wrap all Koog calls in `arrow.core.raise.catch { }`
3. Use adapter functions for type conversions
4. Maintain `context(_: Raise<ProviderError>)` throughout

---

#### Step 4: Implement Helper Functions

Break down complex operations into ≤10 line functions:

```kotlin
context(_: Raise<ProviderError>)
private suspend fun buildPrompt(messages: List<Message>): Prompt =
    Prompt(messages.map { it.toKoog().bind() })

context(_: Raise<ProviderError>)
private fun convertModel(model: Model): LLModel =
    model.toKoog().bind()

context(_: Raise<ProviderError>)
private fun convertParams(params: RequestParameters): LLMParams =
    params.toKoog().bind()

context(_: Raise<ProviderError>)
private fun convertTools(tools: List<AI<*, *>>): List<ToolDescriptor> =
    tools.map { it.toKoogToolDescriptor() }
```

---

#### Step 5: Run Constitutional Verification

Before committing:

```bash
# 1. Run full build with Detekt
./gradlew build

# 2. Fix ALL Detekt violations (zero tolerance)
# Repeat until: BUILD SUCCESSFUL with 0 Detekt violations

# 3. Verify tests pass
./gradlew jvmTest

# 4. Verify all existing tests pass WITHOUT modification
# Success criteria: AgentTest, AgentStructuredOutputTest, AgentToolsTest all pass
```

**Constitutional Checklist**:
- ✅ All functions ≤10 lines
- ✅ All error handling uses Arrow Raise context
- ✅ No try-catch blocks (use `catch { }` from Arrow)
- ✅ All data structures immutable
- ✅ No `var` declarations
- ✅ Zero Detekt violations
- ✅ All tests pass

---

## For Library Users

### Good News: Zero Changes Required!

If you're using the OpenAIProvider, **you don't need to change anything**. The public API remains identical.

**Before and After Migration** (no difference):

```kotlin
import predictable.agent.*

val provider = OpenAIProvider(
    baseUrl = null,  // or custom URL
    apiKey = System.getenv("OPENAI_API_KEY")
)

// Chat completion - same as before
val response: AgentResponse.Text = provider.chatCompletion(
    model = Model("gpt-4"),
    messages = listOf(Message.User("Hello!")),
    tools = emptyList(),
    parameters = RequestParameters(temperature = 0.7),
    toolCallBack = null
)

// Structured output - same as before
val structured: AgentResponse.Structured<MyType> = provider.chatCompletionStructured(
    messages = messages,
    model = Model("gpt-4"),
    tools = emptyList(),
    schema = MyType.schema,
    parameters = RequestParameters(),
    toolCallBack = null
)

// Streaming - same as before
val stream: AgentResponse.StringStream = provider.chatCompletionStream(
    model = Model("gpt-4"),
    messages = messages,
    tools = emptyList(),
    parameters = RequestParameters(),
    toolCallBack = null
)
```

### What Changed Internally (FYI)

- **Dependency**: `com.aallam.openai:openai-client` → `ai.koog:koog-agents`
- **HTTP Client**: Direct OpenAI client → Koog's `OpenAILLMClient`
- **Error Handling**: Exceptions → Arrow Raise context (internal only)
- **Type Adapters**: New conversion layer between types

### Benefits You Get

1. **Persistence Support** (future): Koog provides built-in conversation persistence
2. **History Management** (future): Automatic message history compression
3. **Multi-LLM Support** (future): Easy switching between providers
4. **Better Observability** (future): Koog integrates with OpenTelemetry

---

## Troubleshooting

### Build Fails with Dependency Conflict

**Symptom**: `Could not resolve ai.koog:koog-agents:0.5.0`

**Solution**:
```bash
# 1. Verify Maven Central is in repositories
# In build.gradle.kts or settings.gradle.kts:
repositories {
    mavenCentral()
}

# 2. Clear Gradle cache
./gradlew --stop
rm -rf ~/.gradle/caches

# 3. Sync dependencies
./gradlew --refresh-dependencies build
```

### Tests Fail After Migration

**Symptom**: Existing tests fail with type errors or null pointer exceptions

**Diagnosis**:
1. Check adapter functions are called correctly
2. Verify metadata generation for Assistant/ToolCall messages
3. Ensure error wrapping with `catch { }` is present

**Solution**:
```bash
# Run specific test with verbose output
./gradlew jvmTest --tests "AgentTest" --info

# Check for adapter conversion errors
# Look for ConversionError.* in logs
```

### Detekt Violations After Implementation

**Symptom**: `./gradlew build` fails with Detekt violations

**Common Violations**:
1. **Function too long**: Break into smaller functions (≤10 lines)
2. **Complex expression**: Extract into named variable
3. **Missing context receiver**: Add `context(_: Raise<Error>)` to signature

**Solution**:
```bash
# Run Detekt with report
./gradlew detekt

# View report
open build/reports/detekt/detekt.html

# Fix violations, then re-run
./gradlew build
```

### Performance Degradation

**Symptom**: Requests take longer after migration

**Diagnosis**:
```kotlin
// Add timing around Koog calls
val start = Clock.System.now()
val responses = koogClient.execute(prompt, model, tools)
val duration = Clock.System.now() - start
logger.debug { "Koog execution took ${duration.inWholeMilliseconds}ms" }
```

**Expected**: < 5ms overhead compared to openai-client (network time dominates)

**If > 50ms overhead**: Check for:
- Unnecessary type conversions in loops
- Missing caching of converted models
- Excessive logging

---

## Agent Integration

### Use Koog Integration Expert

For all Koog-related implementation tasks, use the specialized agent:

```bash
# In Claude Code, invoke the agent:
# "Use the koog-integration-expert agent to implement message adapters"
```

**Agent Capabilities**:
- Understands Koog API and types
- Knows Arrow Raise wrapping patterns
- Enforces constitutional compliance (≤10 lines, immutability)
- References `/tmp/koog` repository for API details

### Verification with kotlin-pro Agent

After implementation, verify constitutional compliance:

```bash
# In Claude Code:
# "Use the kotlin-pro agent to verify OpenAIProvider.kt for constitutional compliance"
```

**Verification Checklist**:
- All functions ≤10 lines
- Arrow Raise context used throughout
- No exceptions thrown (only raised)
- Immutable data structures only
- Zero Detekt violations

---

## Quick Reference

### Key Files

| File | Purpose |
|------|---------|
| `OpenAIProvider.kt` | Main provider class (public API unchanged) |
| `adapters/MessageAdapters.kt` | Message type conversions |
| `adapters/ModelAdapters.kt` | Model type conversions |
| `adapters/ParameterAdapters.kt` | Request parameter conversions |
| `adapters/ResponseAdapters.kt` | Response aggregation and conversion |
| `adapters/ToolAdapters.kt` | Tool descriptor wrappers |
| `adapters/ConversionErrors.kt` | Typed error definitions |

### Key Imports

```kotlin
// Koog core
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.message.Message as KoogMessage
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.params.LLMParams
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.streaming.StreamFrame

// Arrow Raise
import arrow.core.raise.Raise
import arrow.core.raise.catch
import arrow.core.raise.context.raise
import arrow.core.raise.context.ensure
import arrow.core.raise.context.ensureNotNull

// Existing types
import predictable.agent.*
import predictable.tool.OutputSchema
```

### Common Patterns

**Wrapping Koog Exceptions**:
```kotlin
context(_: Raise<ProviderError>)
suspend fun callKoog(): Result = catch({
    ProviderError.ExecutionFailed(it.message ?: "Unknown")
}) {
    koogClient.execute(...)
}
```

**Converting Message Lists**:
```kotlin
context(_: Raise<ConversionError>)
fun List<Message>.toKoog(): List<KoogMessage> =
    map { it.toKoog().bind() }
```

**Streaming Transformation**:
```kotlin
fun Flow<StreamFrame>.toStringStream(): Flow<String> =
    mapNotNull { frame ->
        when (frame) {
            is StreamFrame.Append -> frame.text
            else -> null
        }
    }
```

---

## Next Steps

1. **Review Architecture**: Read [research.md](./research.md) for design decisions
2. **Understand Types**: Study [data-model.md](./data-model.md) for mappings
3. **Check Contracts**: Review [type-adapters.md](./contracts/type-adapters.md) for adapter specifications
4. **Implement**: Follow this quickstart step-by-step
5. **Verify**: Run constitutional checks and all tests
6. **Submit**: Create PR with all artifacts

---

## References

- **Koog Repository**: `/tmp/koog` (cloned locally)
- **Koog Documentation**: https://docs.koog.ai
- **Koog README**: https://github.com/JetBrains/koog/blob/main/README.md
- **Feature Spec**: [spec.md](./spec.md)
- **Research**: [research.md](./research.md)
- **Data Model**: [data-model.md](./data-model.md)
- **Type Adapters**: [contracts/type-adapters.md](./contracts/type-adapters.md)
- **Constitution**: `/.specify/memory/constitution.md`
- **Koog Integration Agent**: `/.claude/agents/koog-integration-expert.md`
