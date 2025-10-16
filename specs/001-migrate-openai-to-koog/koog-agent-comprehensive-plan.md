# Comprehensive Plan: AgentProvider with Koog AIAgent Framework

**Date**: 2025-10-16
**Purpose**: Migrate from OpenAIProvider to AgentProvider using Koog's full AIAgent framework with built-in persistence and history management

---

## Executive Summary

**CRITICAL DISCOVERY**: We should NOT be using just Koog's LLMClient layer. Koog provides a complete AIAgent framework with:
- ✅ **Built-in persistence** (AgentCheckpointData with PersistenceStorageProvider)
- ✅ **Automatic history compression** (HistoryCompressionStrategies)
- ✅ **Multi-provider support** (OpenAI, Anthropic, Google, Bedrock, DeepSeek, OpenRouter, Ollama)
- ✅ **Tool integration** (ToolRegistry)
- ✅ **Feature system** (installFeatures for extensibility)

**Current Problem**:
1. `OpenAIProvider` is OpenAI-specific (should be provider-agnostic)
2. We're using low-level LLMClient when we should use AIAgent
3. We're building custom persistence/history when Koog already has it
4. Models are wrong in OpenAIProvider (line 202)

**Solution**:
Create `AgentProvider` that wraps Koog's AIAgent framework and infers the correct provider from the Model.

---

## Architecture Overview

### Current (Wrong) Architecture
```
User Code
    ↓
OpenAIProvider (our wrapper)
    ↓
OpenAILLMClient (Koog low-level)
    ↓
OpenAI API
```

**Problems**:
- ❌ Tied to OpenAI only
- ❌ Missing Koog's persistence
- ❌ Missing Koog's history management
- ❌ Missing Koog's feature system
- ❌ Not using AIAgent framework

### New (Correct) Architecture
```
User Code
    ↓
AgentProvider (provider-agnostic wrapper)
    ↓
Koog AIAgent Framework
    ├─ PromptExecutor (inferred from Model)
    │   ├─ OpenAILLMClient (for gpt-* models)
    │   ├─ AnthropicLLMClient (for claude-* models)
    │   ├─ GoogleLLMClient (for gemini-* models)
    │   └─ OllamaClient (for llama-* models)
    ├─ Persistence Feature (via installFeatures)
    ├─ History Compression (via installFeatures)
    └─ Tool Registry
```

**Benefits**:
- ✅ Multi-provider support (auto-infer from Model)
- ✅ Koog's built-in persistence
- ✅ Koog's automatic history management
- ✅ Extensible via Koog's feature system
- ✅ Single unified interface

---

## Koog AIAgent Framework Components

### 1. AIAgent Creation
```kotlin
import ai.koog.agents.core.agent.AIAgent
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.llm.OpenAIModels

// Create PromptExecutor
val llmClient = OpenAILLMClient(apiKey)
val promptExecutor = SingleLLMPromptExecutor(llmClient)

// Create AIAgent with features
val agent = AIAgent(
    promptExecutor = promptExecutor,
    llmModel = OpenAIModels.Chat.GPT4o,
    toolRegistry = ToolRegistry { /* tools */ },
    systemPrompt = "You are a helpful assistant",
    temperature = 0.7,
    maxIterations = 50,
    installFeatures = {
        // Install persistence
        install(PersistenceFeature) {
            storage = InMemoryPersistenceStorageProvider()
            enableAutomaticPersistence = true
            rollbackStrategy = RollbackStrategy.Default
        }

        // Install history compression
        install(HistoryCompressionFeature) {
            strategy = HistoryCompressionStrategy.TokenLimit(maxTokens = 4000)
        }
    }
)

// Use agent
val response: String = agent.run("Hello, world!")
```

### 2. Multi-Provider Support

Koog supports these providers out of the box:

| Provider | LLMClient | Models Example |
|----------|-----------|----------------|
| OpenAI | `OpenAILLMClient` | gpt-4, gpt-4o, gpt-3.5-turbo |
| Anthropic | `AnthropicLLMClient` | claude-3-opus, claude-3-sonnet |
| Google | `GoogleLLMClient` | gemini-pro, gemini-ultra |
| Bedrock | `BedrockLLMClient` | claude-v2, titan-text |
| DeepSeek | `DeepSeekLLMClient` | deepseek-chat |
| OpenRouter | `OpenRouterLLMClient` | Any model via OpenRouter |
| Ollama | `OllamaClient` | llama3, mistral, phi |

### 3. Built-in Persistence

```kotlin
import ai.koog.agents.snapshot.feature.PersistenceFeature
import ai.koog.agents.snapshot.providers.InMemoryPersistenceStorageProvider
import ai.koog.agents.snapshot.providers.FilePersistenceStorageProvider
import ai.koog.agents.features.sql.providers.H2PersistenceStorageProvider

// In-memory (for testing)
val inMemoryStorage = InMemoryPersistenceStorageProvider()

// File-based (JSON files)
val fileStorage = FilePersistenceStorageProvider(basePath = "/path/to/checkpoints")

// SQL-based (H2, PostgreSQL, MySQL)
val sqlStorage = H2PersistenceStorageProvider(jdbcUrl = "jdbc:h2:mem:test")

// Install in agent
installFeatures = {
    install(PersistenceFeature) {
        storage = inMemoryStorage  // or fileStorage, sqlStorage
        enableAutomaticPersistence = true
        rollbackStrategy = RollbackStrategy.Default
    }
}
```

### 4. Built-in History Compression

```kotlin
import ai.koog.agents.core.dsl.extension.HistoryCompressionStrategy

// Available strategies:
// 1. WholeHistory - Compress entire history into TL;DR
// 2. FromLastNMessages(n) - Keep only last N messages
// 3. FromTimestamp(timestamp) - Keep messages from timestamp
// 4. Chunked(chunkSize) - Process in chunks
// 5. WholeHistoryMultipleSystemMessages - Handle multiple system messages

installFeatures = {
    install(HistoryCompressionFeature) {
        // Option 1: Keep last N
        strategy = HistoryCompressionStrategy.FromLastNMessages(n = 10)

        // Option 2: Whole history summary
        strategy = HistoryCompressionStrategy.WholeHistory

        // Option 3: Chunked processing
        strategy = HistoryCompressionStrategy.Chunked(chunkSize = 5)
    }
}
```

---

## Implementation Plan

### Phase 1: Core Types and Provider Inference

**Goal**: Create provider-agnostic Model type that infers the correct Koog LLMClient

#### Task 1.1: Create ModelProvider Mapping
**File**: `agents/src/commonMain/kotlin/agent/ModelProvider.kt`

```kotlin
package predictable.agent

import ai.koog.prompt.llm.*

/**
 * Maps model name patterns to their provider and Koog LLModel
 */
sealed interface ModelProvider {
    val llModel: LLModel

    data class OpenAI(override val llModel: LLModel) : ModelProvider
    data class Anthropic(override val llModel: LLModel) : ModelProvider
    data class Google(override val llModel: LLModel) : ModelProvider
    data class Ollama(override val llModel: LLModel) : ModelProvider

    companion object {
        fun fromModel(model: Model): ModelProvider = when {
            model.name.startsWith("gpt-") -> {
                val llModel = when (model.name.lowercase()) {
                    "gpt-4o" -> OpenAIModels.Chat.GPT4o
                    "gpt-4o-mini" -> OpenAIModels.Chat.GPT4oMini
                    "gpt-4" -> OpenAIModels.Chat.GPT4
                    "gpt-3.5-turbo" -> OpenAIModels.Chat.GPT35Turbo
                    else -> OpenAIModels.Chat.GPT4o  // default
                }
                OpenAI(llModel)
            }
            model.name.startsWith("claude-") -> {
                val llModel = when (model.name.lowercase()) {
                    "claude-3-opus" -> AnthropicModels.Claude3Opus
                    "claude-3-sonnet" -> AnthropicModels.Claude3Sonnet
                    "claude-3-haiku" -> AnthropicModels.Claude3Haiku
                    else -> AnthropicModels.Claude3Sonnet  // default
                }
                Anthropic(llModel)
            }
            model.name.startsWith("gemini-") -> {
                val llModel = when (model.name.lowercase()) {
                    "gemini-pro" -> GoogleModels.GeminiPro
                    "gemini-ultra" -> GoogleModels.GeminiUltra
                    else -> GoogleModels.GeminiPro  // default
                }
                Google(llModel)
            }
            model.name.startsWith("llama") || model.name.contains("mistral") -> {
                val llModel = OllamaModels.custom(model.name)
                Ollama(llModel)
            }
            else -> throw IllegalArgumentException("Unsupported model: ${model.name}")
        }
    }
}
```

#### Task 1.2: Create PromptExecutorFactory
**File**: `agents/src/commonMain/kotlin/agent/PromptExecutorFactory.kt`

```kotlin
package predictable.agent

import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.google.GoogleLLMClient
import ai.koog.prompt.executor.ollama.client.OllamaClient

/**
 * Factory to create PromptExecutor based on ModelProvider
 */
object PromptExecutorFactory {
    fun create(provider: ModelProvider, apiKey: String, baseUrl: String? = null): PromptExecutor =
        when (provider) {
            is ModelProvider.OpenAI -> {
                val client = if (baseUrl != null) {
                    OpenAILLMClient(apiKey, OpenAIClientSettings(baseUrl = baseUrl))
                } else {
                    OpenAILLMClient(apiKey)
                }
                SingleLLMPromptExecutor(client)
            }
            is ModelProvider.Anthropic -> {
                SingleLLMPromptExecutor(AnthropicLLMClient(apiKey))
            }
            is ModelProvider.Google -> {
                SingleLLMPromptExecutor(GoogleLLMClient(apiKey))
            }
            is ModelProvider.Ollama -> {
                SingleLLMPromptExecutor(OllamaClient(baseUrl ?: "http://localhost:11434"))
            }
        }
}
```

### Phase 2: AgentProvider Implementation

**Goal**: Create provider-agnostic AgentProvider that wraps Koog's AIAgent

#### Task 2.1: Create AgentProvider
**File**: `agents/src/commonMain/kotlin/agent/AgentProvider.kt`

```kotlin
package predictable.agent

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.snapshot.feature.PersistenceFeature
import ai.koog.agents.snapshot.providers.InMemoryPersistenceStorageProvider
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.params.LLMParams
import arrow.core.raise.Raise
import arrow.core.raise.catch

/**
 * Provider-agnostic agent that automatically infers the correct LLM provider
 * from the Model and uses Koog's AIAgent framework with built-in persistence
 * and history management.
 */
class AgentProvider(
    private val apiKey: String,
    private val baseUrl: String? = null,
    private val enablePersistence: Boolean = false,
    private val persistenceStorage: PersistenceStorageProvider<*>? = null
) {
    /**
     * Chat completion with automatic provider inference
     */
    context(_: Raise<ProviderError>)
    suspend fun chatCompletion(
        model: Model,
        messages: List<Message>,
        tools: List<AI<*, *>> = emptyList(),
        parameters: RequestParameters = RequestParameters(),
        toolCallBack: ToolCallback? = null
    ): AgentResponse.Text {
        // Infer provider from model
        val modelProvider = ModelProvider.fromModel(model)

        // Create PromptExecutor
        val promptExecutor = PromptExecutorFactory.create(modelProvider, apiKey, baseUrl)

        // Create tool registry
        val toolRegistry = createToolRegistry(tools)

        // Create AIAgent with features
        val agent = createAgent(
            promptExecutor = promptExecutor,
            llModel = modelProvider.llModel,
            toolRegistry = toolRegistry,
            parameters = parameters
        )

        // Execute
        val userInput = messages.lastOrNull { it is Message.User }?.content ?: ""
        val response = catch({ ProviderError.ExecutionFailed(it.message ?: "Unknown error") }) {
            agent.run(userInput)
        }

        // Convert to AgentResponse
        return AgentResponse.Text(
            value = response,
            metadata = AgentMetadata(
                usage = Usage(promptTokens = 0, completionTokens = 0, totalTokens = 0),  // TODO: extract from agent
                model = model.name
            ),
            messages = messages + Message.Assistant(response)
        )
    }

    private fun createAgent(
        promptExecutor: PromptExecutor,
        llModel: LLModel,
        toolRegistry: ToolRegistry,
        parameters: RequestParameters
    ): AIAgent<String, String> = AIAgent(
        promptExecutor = promptExecutor,
        llmModel = llModel,
        toolRegistry = toolRegistry,
        temperature = parameters.temperature ?: 1.0,
        maxIterations = 50,
        installFeatures = {
            if (enablePersistence) {
                install(PersistenceFeature) {
                    storage = persistenceStorage ?: InMemoryPersistenceStorageProvider()
                    enableAutomaticPersistence = true
                }
            }
            // TODO: Add history compression feature
        }
    )

    private fun createToolRegistry(tools: List<AI<*, *>>): ToolRegistry =
        if (tools.isEmpty()) ToolRegistry.EMPTY
        else ToolRegistry { tools.forEach { tool -> /* register tool */ } }
}
```

### Phase 3: Migration from OpenAIProvider

#### Task 3.1: Rename OpenAIProvider to AgentProvider
**Files to modify**:
- `agents/src/commonMain/kotlin/agent/providers/openai/OpenAIProvider.kt` → `agents/src/commonMain/kotlin/agent/AgentProvider.kt`
- Update all imports across the codebase
- Update tests

#### Task 3.2: Fix Model Issues (Line 202)
**Current problem** (OpenAIProvider.kt:202):
```kotlin
// OLD (wrong):
val llModel = OpenAIModels.Chat.GPT4o  // Hardcoded!
```

**New approach**:
```kotlin
// NEW (correct):
val modelProvider = ModelProvider.fromModel(model)  // Inferred!
val llModel = modelProvider.llModel
```

#### Task 3.3: Remove Custom Persistence Code
Delete these files (no longer needed):
- `agents/src/commonMain/kotlin/agent/persistence/*` (all files)
- `agents/src/commonMain/kotlin/agent/history/*` (all files)

Use Koog's built-in features instead.

### Phase 4: Update Tests

#### Task 4.1: Update Unit Tests
**File**: `agents/src/jvmTest/kotlin/agent/AgentProviderTest.kt`

Test scenarios:
1. ✅ OpenAI model inference (gpt-4, gpt-3.5-turbo)
2. ✅ Anthropic model inference (claude-3-opus, claude-3-sonnet)
3. ✅ Google model inference (gemini-pro)
4. ✅ Ollama model inference (llama3, mistral)
5. ✅ Persistence with InMemoryPersistenceStorageProvider
6. ✅ History compression (if enabled)
7. ✅ Multi-turn conversations with context
8. ✅ Tool calling
9. ✅ Immutable message handling

#### Task 4.2: Integration Tests with Koog Features
**File**: `agents/src/jvmTest/kotlin/agent/KoogFeaturesIntegrationTest.kt`

Test scenarios:
1. ✅ Automatic persistence (checkpoint save/load)
2. ✅ History compression triggers at context limit
3. ✅ Rollback to previous checkpoint
4. ✅ Multi-provider switching (different models in same conversation)
5. ✅ Context window management (100+ messages)

---

## Dependency Updates

### Update `gradle/libs.versions.toml`
```toml
[versions]
koog = "0.5.1"

[libraries]
koog-agents = { module = "ai.koog:koog-agents", version.ref = "koog" }
koog-snapshot = { module = "ai.koog:agents-features-snapshot", version.ref = "koog" }  # For persistence
```

### Update `agents/build.gradle.kts`
```kotlin
commonMain {
    dependencies {
        implementation(libs.koog.agents)
        implementation(libs.koog.snapshot)  // For persistence features
    }
}
```

---

## Migration Checklist

### Pre-Implementation
- [x] Research Koog AIAgent framework
- [x] Research Koog persistence mechanisms
- [x] Research Koog history compression
- [x] Research Koog multi-provider support
- [x] Create comprehensive plan

### Implementation
- [ ] Create `ModelProvider.kt` with inference logic
- [ ] Create `PromptExecutorFactory.kt`
- [ ] Implement `AgentProvider` with Koog AIAgent
- [ ] Add Koog persistence feature integration
- [ ] Add Koog history compression feature integration
- [ ] Rename `OpenAIProvider` → `AgentProvider`
- [ ] Fix model issues (line 202)
- [ ] Delete custom persistence/history code
- [ ] Update all imports and references

### Testing
- [ ] Write unit tests for model inference
- [ ] Write tests for multi-provider support
- [ ] Write tests for Koog persistence
- [ ] Write tests for Koog history compression
- [ ] Write integration tests for context window management
- [ ] Verify all existing tests pass
- [ ] Test with real APIs (OpenAI, Anthropic if available)

### Documentation
- [ ] Update README with new AgentProvider usage
- [ ] Document model inference rules
- [ ] Document how to enable persistence
- [ ] Document how to enable history compression
- [ ] Document supported providers

### Cleanup
- [ ] Remove old persistence types
- [ ] Remove old history compression types
- [ ] Remove OpenAI-specific code
- [ ] Update git history

---

## Example Usage (After Migration)

### Basic Usage (Auto-Infer Provider)
```kotlin
// OpenAI
val provider = AgentProvider(apiKey = "sk-...")
val response = provider.chatCompletion(
    model = Model("gpt-4"),  // Auto-infers OpenAILLMClient
    messages = listOf(Message.User("Hello!"))
)

// Anthropic
val response2 = provider.chatCompletion(
    model = Model("claude-3-opus"),  // Auto-infers AnthropicLLMClient
    messages = listOf(Message.User("Hello!"))
)
```

### With Persistence
```kotlin
val provider = AgentProvider(
    apiKey = "sk-...",
    enablePersistence = true,
    persistenceStorage = InMemoryPersistenceStorageProvider()
)

// Conversation is automatically persisted
val response1 = provider.chatCompletion(
    model = Model("gpt-4"),
    messages = listOf(Message.User("What is 2+2?"))
)

// History is preserved across calls
val response2 = provider.chatCompletion(
    model = Model("gpt-4"),
    messages = listOf(Message.User("What was my previous question?"))
)
// Response: "Your previous question was 'What is 2+2?'"
```

### With Custom Base URL (e.g., OpenAI-compatible)
```kotlin
val provider = AgentProvider(
    apiKey = "...",
    baseUrl = "https://api.openai-compatible-service.com/v1"
)
```

---

## Success Criteria

1. ✅ AgentProvider works with multiple providers (OpenAI, Anthropic, Google, Ollama)
2. ✅ Model inference is automatic and correct
3. ✅ Koog's persistence works (checkpoint save/load)
4. ✅ Koog's history compression works (context window management)
5. ✅ All existing tests pass without modification
6. ✅ No custom persistence/history code (use Koog's built-in)
7. ✅ Constitutional compliance (≤10 lines per function)
8. ✅ Zero Detekt violations
9. ✅ Build succeeds (`./gradlew build`)

---

## Constitutional Compliance Notes

All code must follow constitution.md:
- ✅ Pure functional programming
- ✅ All functions ≤10 lines
- ✅ Arrow Raise context for errors
- ✅ Immutable data structures
- ✅ No for/while loops (use map, filter, fold)
- ✅ Zero Detekt violations

**Verification**:
```bash
./gradlew build  # Must pass with 0 violations
./gradlew jvmTest  # All tests must pass
```

---

## References

- **Koog AIAgent**: `/tmp/koog/agents/agents-core/src/commonMain/kotlin/ai/koog/agents/core/agent/AIAgent.kt`
- **Koog Persistence**: `/tmp/koog/agents/agents-features/agents-features-snapshot/`
- **Koog History**: `/tmp/koog/agents/agents-core/src/commonMain/kotlin/ai/koog/agents/core/dsl/extension/HistoryCompressionStrategies.kt`
- **Koog Multi-Provider**: `/tmp/koog/prompt/prompt-executor/prompt-executor-clients/`
- **Koog Examples**: `/tmp/koog/prompt/prompt-executor/prompt-executor-llms-all/src/commonMain/kotlin/ai/koog/prompt/executor/llms/all/SimplePromptExecutors.kt`
- **Constitution**: `.specify/memory/constitution.md`

---

## Next Steps

1. **Delete custom persistence/history code** (agents/src/commonMain/kotlin/agent/persistence/, agents/src/commonMain/kotlin/agent/history/)
2. **Implement ModelProvider.kt** with inference logic
3. **Implement PromptExecutorFactory.kt**
4. **Implement AgentProvider** with Koog AIAgent
5. **Update tests** to use AgentProvider and Koog features
6. **Run constitutional verification** (`./gradlew build`)
7. **Document** the new architecture

**Priority**: HIGH - This fixes fundamental architectural issues
