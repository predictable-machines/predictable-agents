# Data Model: Koog Integration Type Mappings

**Feature**: OpenAI Provider Migration to Koog
**Date**: 2025-10-16
**Purpose**: Document entity mappings, type conversions, and validation rules for Koog integration

---

## Overview

This document defines the bidirectional type mappings between the existing Predictable Agents types and Koog framework types. All conversions preserve immutability and use Arrow Raise context for error handling.

---

## Entity Mappings

### 1. Message Types

#### 1.1 System Message

**Existing Type**:
```kotlin
package predictable.agent

data class Message.System(
    val content: String
) : Message
```

**Koog Type**:
```kotlin
package ai.koog.prompt.message

data class Message.System(
    val content: String
) : Message
```

**Mapping**: Direct 1:1 correspondence, identical structure

**Validation Rules**:
- `content` must not be empty (validate before conversion)
- No special characters that require escaping

**Conversion Functions**:
```kotlin
// Existing → Koog
context(_: Raise<ConversionError>)
fun Message.System.toKoog(): ai.koog.prompt.message.Message.System {
    ensure(content.isNotBlank()) { ConversionError.EmptyContent }
    return ai.koog.prompt.message.Message.System(content)
}

// Koog → Existing
fun ai.koog.prompt.message.Message.System.fromKoog(): Message.System =
    Message.System(content)
```

---

#### 1.2 User Message

**Existing Type**:
```kotlin
package predictable.agent

data class Message.User(
    val content: String
) : Message
```

**Koog Type**:
```kotlin
package ai.koog.prompt.message

data class Message.User(
    val content: String,
    val attachments: List<Attachment> = emptyList()
) : Message, Message.WithAttachments
```

**Mapping**: Existing type → Koog with empty attachments

**Validation Rules**:
- `content` must not be empty
- Attachments not currently supported (empty list for Koog)

**Conversion Functions**:
```kotlin
// Existing → Koog
context(_: Raise<ConversionError>)
fun Message.User.toKoog(): ai.koog.prompt.message.Message.User {
    ensure(content.isNotBlank()) { ConversionError.EmptyContent }
    return ai.koog.prompt.message.Message.User(
        content = content,
        attachments = emptyList()  // No attachment support currently
    )
}

// Koog → Existing
fun ai.koog.prompt.message.Message.User.fromKoog(): Message.User =
    Message.User(content)  // Ignore attachments
```

**Future Enhancement**: Support attachments if needed

---

#### 1.3 Assistant Message

**Existing Type**:
```kotlin
package predictable.agent

data class Message.Assistant(
    val content: String
) : Message
```

**Koog Type**:
```kotlin
package ai.koog.prompt.message

data class Message.Assistant(
    val content: String,
    val finishReason: String?,
    val metaInfo: ResponseMetaInfo
) : Message.Response
```

**Mapping**: Existing type → Koog with generated metadata

**Validation Rules**:
- `content` can be empty (valid for tool-only responses)
- `finishReason` nullable (optional)
- `metaInfo` required by Koog (generate if not present)

**Conversion Functions**:
```kotlin
// Existing → Koog (requires metadata)
context(_: Raise<ConversionError>)
fun Message.Assistant.toKoog(
    finishReason: String? = null,
    metaInfo: ResponseMetaInfo
): ai.koog.prompt.message.Message.Assistant =
    ai.koog.prompt.message.Message.Assistant(
        content = content,
        finishReason = finishReason,
        metaInfo = metaInfo
    )

// Koog → Existing (discard metadata)
fun ai.koog.prompt.message.Message.Assistant.fromKoog(): Message.Assistant =
    Message.Assistant(content)
```

**Metadata Loss**: Existing type doesn't store `metaInfo` - acceptable for backward compatibility

---

#### 1.4 Tool Call Message

**Existing Type** (inferred from spec.md):
```kotlin
package predictable.agent

data class Message.ToolCall(
    val id: String,
    val name: String,
    val arguments: String  // JSON string
) : Message
```

**Koog Type**:
```kotlin
package ai.koog.prompt.message

data class Message.Tool.Call(
    val id: String?,
    val tool: String,
    val content: String,
    val metaInfo: ResponseMetaInfo
) : Message.Tool, Message.Response
```

**Mapping**: Field name differences (`name` ↔ `tool`, `arguments` ↔ `content`)

**Validation Rules**:
- `id` required in existing type, optional in Koog (generate UUID if needed)
- `name`/`tool` must be non-empty
- `arguments`/`content` must be valid JSON

**Conversion Functions**:
```kotlin
// Existing → Koog
context(_: Raise<ConversionError>)
fun Message.ToolCall.toKoog(
    metaInfo: ResponseMetaInfo
): ai.koog.prompt.message.Message.Tool.Call {
    ensure(name.isNotBlank()) { ConversionError.EmptyToolName }
    return ai.koog.prompt.message.Message.Tool.Call(
        id = id,
        tool = name,
        content = arguments,
        metaInfo = metaInfo
    )
}

// Koog → Existing
context(_: Raise<ConversionError>)
fun ai.koog.prompt.message.Message.Tool.Call.fromKoog(): Message.ToolCall {
    ensureNotNull(id) { ConversionError.MissingToolCallId }
    return Message.ToolCall(
        id = id,
        name = tool,
        arguments = content
    )
}
```

---

#### 1.5 Tool Result Message

**Existing Type** (inferred):
```kotlin
package predictable.agent

data class Message.ToolResult(
    val id: String,
    val content: String
) : Message
```

**Koog Type**:
```kotlin
package ai.koog.prompt.message

data class Message.Tool.Result(
    val id: String?,
    val content: String
) : Message.Tool
```

**Mapping**: Nearly identical, `id` nullability difference

**Validation Rules**:
- `id` required in existing type, optional in Koog
- `content` can be empty (valid for void tool results)

**Conversion Functions**:
```kotlin
// Existing → Koog
fun Message.ToolResult.toKoog(): ai.koog.prompt.message.Message.Tool.Result =
    ai.koog.prompt.message.Message.Tool.Result(
        id = id,
        content = content
    )

// Koog → Existing
context(_: Raise<ConversionError>)
fun ai.koog.prompt.message.Message.Tool.Result.fromKoog(): Message.ToolResult {
    ensureNotNull(id) { ConversionError.MissingToolResultId }
    return Message.ToolResult(
        id = id,
        content = content
    )
}
```

---

### 2. Model Configuration

#### 2.1 Model

**Existing Type**:
```kotlin
package predictable.agent

data class Model(
    val name: String  // e.g., "gpt-4", "gpt-3.5-turbo"
)
```

**Koog Type**:
```kotlin
package ai.koog.prompt.llm

interface LLModel {
    val id: String
    val capabilities: Set<LLMCapability>
    fun supports(capability: LLMCapability): Boolean
}

// Concrete implementations
object OpenAIModels {
    val GPT4o: LLModel
    val GPT4oMini: LLModel
    // etc.
}
```

**Mapping**: Model name string → LLModel lookup

**Validation Rules**:
- `name` must correspond to valid OpenAI model ID
- Model must exist in Koog's OpenAIModels catalog

**Conversion Functions**:
```kotlin
// Existing → Koog
context(_: Raise<ConversionError>)
fun Model.toKoog(): LLModel =
    when (name.lowercase()) {
        "gpt-4o" -> OpenAIModels.Chat.GPT4o
        "gpt-4o-mini" -> OpenAIModels.Chat.GPT4oMini
        "gpt-4-turbo" -> OpenAIModels.Chat.GPT4Turbo
        "gpt-4" -> OpenAIModels.Chat.GPT4
        "gpt-3.5-turbo" -> OpenAIModels.Chat.GPT35Turbo
        else -> raise(ConversionError.UnknownModel(name))
    }

// Koog → Existing
fun LLModel.fromKoog(): Model =
    Model(name = id)
```

**Extensibility**: Add new model mappings as needed

---

#### 2.2 Request Parameters

**Existing Type**:
```kotlin
package predictable.agent

data class RequestParameters(
    val temperature: Double? = null,
    val maxTokens: Int? = null,
    val topP: Double? = null,
    val frequencyPenalty: Double? = null,
    val presencePenalty: Double? = null,
    val stop: List<String>? = null
)
```

**Koog Type**:
```kotlin
package ai.koog.prompt.params

open class LLMParams(
    val temperature: Double? = null,
    val maxTokens: Int? = null,
    val topP: Double? = null,
    val frequencyPenalty: Double? = null,
    val presencePenalty: Double? = null,
    val stop: List<String>? = null,
    val schema: Schema? = null,
    val toolChoice: ToolChoice? = null,
    // ... many more parameters
)
```

**Mapping**: Subset mapping (existing parameters → Koog parameters)

**Validation Rules**:
- `temperature` ∈ [0.0, 2.0]
- `topP` ∈ [0.0, 1.0]
- `maxTokens` > 0
- `frequencyPenalty` ∈ [-2.0, 2.0]
- `presencePenalty` ∈ [-2.0, 2.0]

**Conversion Functions**:
```kotlin
// Existing → Koog
context(_: Raise<ConversionError>)
fun RequestParameters.toKoog(): LLMParams {
    temperature?.let { ensure(it in 0.0..2.0) { ConversionError.InvalidTemperature(it) } }
    topP?.let { ensure(it in 0.0..1.0) { ConversionError.InvalidTopP(it) } }
    maxTokens?.let { ensure(it > 0) { ConversionError.InvalidMaxTokens(it) } }

    return LLMParams(
        temperature = temperature,
        maxTokens = maxTokens,
        topP = topP,
        frequencyPenalty = frequencyPenalty,
        presencePenalty = presencePenalty,
        stop = stop
    )
}

// Koog → Existing (lossy, ignores extra params)
fun LLMParams.fromKoog(): RequestParameters =
    RequestParameters(
        temperature = temperature,
        maxTokens = maxTokens,
        topP = topP,
        frequencyPenalty = frequencyPenalty,
        presencePenalty = presencePenalty,
        stop = stop
    )
```

---

### 3. Response Types

#### 3.1 Agent Response (Text)

**Existing Type**:
```kotlin
package predictable.agent

data class AgentResponse.Text(
    val value: String,
    val metadata: AgentMetadata,
    val messages: List<Message>
)
```

**Koog Type**:
```kotlin
// Koog returns List<Message.Response>
// Must aggregate into AgentResponse.Text
```

**Mapping**: Extract text from Koog responses, preserve metadata

**Validation Rules**:
- At least one message in responses
- Final message should be Assistant type
- Metadata extracted from ResponseMetaInfo

**Conversion Functions**:
```kotlin
// Koog responses → Existing AgentResponse.Text
context(_: Raise<ConversionError>)
fun List<ai.koog.prompt.message.Message.Response>.toAgentResponseText(
    modelId: String
): AgentResponse.Text {
    ensure(isNotEmpty()) { ConversionError.EmptyResponses }

    val lastAssistant = filterIsInstance<ai.koog.prompt.message.Message.Assistant>().lastOrNull()
    ensureNotNull(lastAssistant) { ConversionError.NoAssistantResponse }

    val metadata = createAgentMetadata(lastAssistant.metaInfo, modelId)
    val convertedMessages = map { it.fromKoog().bind() }

    return AgentResponse.Text(
        value = lastAssistant.content,
        metadata = metadata,
        messages = convertedMessages
    )
}
```

---

#### 3.2 Agent Response (Structured)

**Existing Type**:
```kotlin
package predictable.agent

data class AgentResponse.Structured<T>(
    val value: T,
    val metadata: AgentMetadata,
    val messages: List<Message>
)
```

**Koog Integration**:
- Use Koog's structured output via JSON schema
- Parse response content as JSON
- Validate against schema

**Conversion Functions**:
```kotlin
// Koog responses → Existing AgentResponse.Structured
context(_: Raise<ConversionError>)
fun <T> List<ai.koog.prompt.message.Message.Response>.toAgentResponseStructured(
    schema: OutputSchema<T>,
    modelId: String
): AgentResponse.Structured<T> {
    ensure(isNotEmpty()) { ConversionError.EmptyResponses }

    val lastAssistant = filterIsInstance<ai.koog.prompt.message.Message.Assistant>().lastOrNull()
    ensureNotNull(lastAssistant) { ConversionError.NoAssistantResponse }
    ensureNotNull(lastAssistant.content) { ConversionError.EmptyStructuredContent }

    val parsed = catch({ ConversionError.JsonParsingFailed(it.message) }) {
        schema.outputFromJson(lastAssistant.content)
    }

    val metadata = createAgentMetadata(lastAssistant.metaInfo, modelId)
    val convertedMessages = map { it.fromKoog().bind() }

    return AgentResponse.Structured(
        value = parsed,
        metadata = metadata,
        messages = convertedMessages
    )
}
```

---

#### 3.3 Agent Response (Streaming)

**Existing Type**:
```kotlin
package predictable.agent

data class AgentResponse.StringStream(
    val value: Flow<String>
)
```

**Koog Type**:
```kotlin
// Koog returns Flow<StreamFrame>
sealed interface StreamFrame {
    data class Append(val text: String) : StreamFrame
    data class ToolCall(val id: String?, val name: String, val arguments: String) : StreamFrame
    data class End(val finishReason: String?, val metaInfo: ResponseMetaInfo) : StreamFrame
}
```

**Mapping**: Extract text from Append frames, ignore End/ToolCall

**Conversion Functions**:
```kotlin
// Koog stream → Existing stream
fun Flow<StreamFrame>.toStringStream(): Flow<String> =
    mapNotNull { frame ->
        when (frame) {
            is StreamFrame.Append -> frame.text
            is StreamFrame.End -> null
            is StreamFrame.ToolCall -> null  // Handle separately if needed
        }
    }

// Wrap in AgentResponse.StringStream
fun Flow<StreamFrame>.toAgentResponseStringStream(): AgentResponse.StringStream =
    AgentResponse.StringStream(value = toStringStream())
```

---

### 4. Tool Definitions

#### 4.1 Tool Descriptor

**Existing Type** (inferred):
```kotlin
package predictable

interface AI<TArgs, TResult> {
    val name: String
    val description: String
    fun schemaAsJson(): JsonObject
    suspend fun execute(args: TArgs): TResult
}
```

**Koog Type**:
```kotlin
package ai.koog.agents.core.tools

interface ToolDescriptor {
    val name: String
    val description: String?
    fun paramsToJsonObject(): JsonObject
}
```

**Mapping**: Wrap AI<*, *> to implement ToolDescriptor

**Conversion Functions**:
```kotlin
// Existing → Koog ToolDescriptor
fun AI<*, *>.toKoogToolDescriptor(): ToolDescriptor =
    object : ToolDescriptor {
        override val name: String = this@toKoogToolDescriptor.name
        override val description: String? = this@toKoogToolDescriptor.description
        override fun paramsToJsonObject(): JsonObject =
            this@toKoogToolDescriptor.schemaAsJson()
    }
```

---

### 5. Metadata

#### 5.1 Agent Metadata

**Existing Type**:
```kotlin
package predictable.agent

data class AgentMetadata(
    val usage: Usage,
    val model: String
)

data class Usage(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int
)
```

**Koog Type**:
```kotlin
package ai.koog.prompt.message

data class ResponseMetaInfo(
    val totalTokensCount: Int?,
    val inputTokensCount: Int?,
    val outputTokensCount: Int?,
    val timestamp: Instant
)
```

**Mapping**: Direct correspondence with different field names

**Conversion Functions**:
```kotlin
// Koog → Existing
fun ResponseMetaInfo.toUsage(): Usage =
    Usage(
        promptTokens = inputTokensCount ?: 0,
        completionTokens = outputTokensCount ?: 0,
        totalTokens = totalTokensCount ?: 0
    )

fun createAgentMetadata(metaInfo: ResponseMetaInfo, modelId: String): AgentMetadata =
    AgentMetadata(
        usage = metaInfo.toUsage(),
        model = modelId
    )
```

---

## Error Types

### Conversion Errors

```kotlin
package predictable.agent.providers.openai

sealed interface ConversionError {
    val message: String

    data class EmptyContent(
        override val message: String = "Message content cannot be empty"
    ) : ConversionError

    data class EmptyToolName(
        override val message: String = "Tool name cannot be empty"
    ) : ConversionError

    data class MissingToolCallId(
        override val message: String = "Tool call ID is required"
    ) : ConversionError

    data class MissingToolResultId(
        override val message: String = "Tool result ID is required"
    ) : ConversionError

    data class UnknownModel(
        val modelName: String,
        override val message: String = "Unknown model: $modelName"
    ) : ConversionError

    data class InvalidTemperature(
        val value: Double,
        override val message: String = "Temperature must be in [0.0, 2.0], got $value"
    ) : ConversionError

    data class InvalidTopP(
        val value: Double,
        override val message: String = "TopP must be in [0.0, 1.0], got $value"
    ) : ConversionError

    data class InvalidMaxTokens(
        val value: Int,
        override val message: String = "MaxTokens must be > 0, got $value"
    ) : ConversionError

    data class EmptyResponses(
        override val message: String = "Koog returned empty responses"
    ) : ConversionError

    data class NoAssistantResponse(
        override val message: String = "No assistant message in responses"
    ) : ConversionError

    data class EmptyStructuredContent(
        override val message: String = "Structured output content is empty"
    ) : ConversionError

    data class JsonParsingFailed(
        val error: String?,
        override val message: String = "JSON parsing failed: $error"
    ) : ConversionError
}
```

---

## State Transitions

### Chat Completion Flow

```
[User Request]
    ↓
[Convert Messages: Existing → Koog]
    ↓
[Convert Model: Model → LLModel]
    ↓
[Convert Parameters: RequestParameters → LLMParams]
    ↓
[Convert Tools: List<AI<*, *>> → List<ToolDescriptor>]
    ↓
[Execute Koog: client.execute(prompt, model, tools)]
    ↓ (suspend, may throw exceptions)
[Wrap in Raise: catch { } → typed errors]
    ↓
[Convert Responses: List<Message.Response> → AgentResponse]
    ↓
[Return: AgentResponse.Text]
```

### Streaming Flow

```
[User Request]
    ↓
[Convert Messages/Model/Params/Tools as above]
    ↓
[Execute Koog Streaming: client.executeStreaming(...)]
    ↓ (returns Flow<StreamFrame>)
[Map Stream: Flow<StreamFrame> → Flow<String>]
    ↓
[Wrap in Response: AgentResponse.StringStream]
    ↓
[Return: AgentResponse.StringStream]
```

### Tool Calling Flow

```
[Execute with Tools]
    ↓
[Koog Returns: List<Message.Response> including Tool.Call]
    ↓
[Filter Tool Calls: filterIsInstance<Message.Tool.Call>()]
    ↓
[Execute Tools: via ToolCallback]
    ↓
[Convert Results: → Message.Tool.Result]
    ↓
[Add to Conversation: messages += toolResults]
    ↓
[Re-execute: client.execute with updated messages]
    ↓
[Get Final Response: Message.Assistant]
```

---

## Validation Summary

| Entity | Validation Rule | Error Type |
|--------|----------------|------------|
| System Message | content.isNotBlank() | ConversionError.EmptyContent |
| User Message | content.isNotBlank() | ConversionError.EmptyContent |
| Tool Call | name.isNotBlank() | ConversionError.EmptyToolName |
| Tool Call | id != null | ConversionError.MissingToolCallId |
| Tool Result | id != null | ConversionError.MissingToolResultId |
| Model | name in known models | ConversionError.UnknownModel |
| Temperature | value in [0.0, 2.0] | ConversionError.InvalidTemperature |
| TopP | value in [0.0, 1.0] | ConversionError.InvalidTopP |
| MaxTokens | value > 0 | ConversionError.InvalidMaxTokens |
| Responses | isNotEmpty() | ConversionError.EmptyResponses |
| Assistant Response | lastOrNull() != null | ConversionError.NoAssistantResponse |

---

## Immutability Guarantees

All types in this data model are immutable:

- ✅ Existing types use `val` properties
- ✅ Koog types use `val` properties (verified in research.md)
- ✅ Conversion functions return new instances, never mutate
- ✅ Collections are immutable (List, not MutableList)
- ✅ No var declarations in any conversion logic

---

## References

- **Feature Spec**: `/Users/raulraja/predictable-machines/predictable-agents/specs/001-migrate-openai-to-koog/spec.md`
- **Research**: `/Users/raulraja/predictable-machines/predictable-agents/specs/001-migrate-openai-to-koog/research.md`
- **Koog Message Types**: `/tmp/koog/prompt/prompt-model/src/commonMain/kotlin/ai/koog/prompt/message/Message.kt`
- **Koog LLM Types**: `/tmp/koog/prompt/prompt-llm/src/commonMain/kotlin/ai/koog/prompt/llm/LLModel.kt`
- **Koog Parameters**: `/tmp/koog/prompt/prompt-model/src/commonMain/kotlin/ai/koog/prompt/params/LLMParams.kt`
- **Constitution**: `/Users/raulraja/predictable-machines/predictable-agents/.specify/memory/constitution.md`
