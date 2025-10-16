# Type Adapter Contracts

**Feature**: OpenAI Provider Migration to Koog
**Date**: 2025-10-16
**Purpose**: Define contracts for type conversion functions between existing and Koog types

---

## Overview

This document specifies the contracts (function signatures, pre/post conditions, error handling) for all type adapter functions used in the Koog integration. All adapters comply with constitutional requirements (≤10 lines, Arrow Raise context, immutability).

---

## Message Adapters

### Contract: `Message.System.toKoog()`

**Signature**:
```kotlin
context(_: Raise<ConversionError>)
fun predictable.agent.Message.System.toKoog(): ai.koog.prompt.message.Message.System
```

**Preconditions**:
- `content` must not be blank

**Postconditions**:
- Returns new Koog System message with identical content
- Original message unchanged (immutability)

**Error Conditions**:
- Raises `ConversionError.EmptyContent` if content is blank

**Complexity**: O(1) - direct field copy

---

### Contract: `Message.System.fromKoog()`

**Signature**:
```kotlin
fun ai.koog.prompt.message.Message.System.fromKoog(): predictable.agent.Message.System
```

**Preconditions**: None (Koog guarantees valid messages)

**Postconditions**:
- Returns new existing System message with identical content
- Original Koog message unchanged

**Error Conditions**: None (cannot fail)

**Complexity**: O(1) - direct field copy

---

### Contract: `Message.User.toKoog()`

**Signature**:
```kotlin
context(_: Raise<ConversionError>)
fun predictable.agent.Message.User.toKoog(): ai.koog.prompt.message.Message.User
```

**Preconditions**:
- `content` must not be blank

**Postconditions**:
- Returns new Koog User message with identical content
- Attachments field set to empty list
- Original message unchanged

**Error Conditions**:
- Raises `ConversionError.EmptyContent` if content is blank

**Complexity**: O(1) - direct field copy + empty list

---

### Contract: `Message.User.fromKoog()`

**Signature**:
```kotlin
fun ai.koog.prompt.message.Message.User.fromKoog(): predictable.agent.Message.User
```

**Preconditions**: None

**Postconditions**:
- Returns new existing User message with identical content
- Discards attachments (not supported in existing type)
- Original Koog message unchanged

**Error Conditions**: None

**Complexity**: O(1) - direct field copy

---

### Contract: `Message.Assistant.toKoog()`

**Signature**:
```kotlin
context(_: Raise<ConversionError>)
fun predictable.agent.Message.Assistant.toKoog(
    finishReason: String? = null,
    metaInfo: ai.koog.prompt.message.ResponseMetaInfo
): ai.koog.prompt.message.Message.Assistant
```

**Preconditions**:
- `metaInfo` must be valid ResponseMetaInfo instance

**Postconditions**:
- Returns new Koog Assistant message with:
  - content from existing message
  - provided finishReason (nullable)
  - provided metaInfo
- Original message unchanged

**Error Conditions**: None (content can be empty)

**Complexity**: O(1) - direct field assignment

---

### Contract: `Message.Assistant.fromKoog()`

**Signature**:
```kotlin
fun ai.koog.prompt.message.Message.Assistant.fromKoog(): predictable.agent.Message.Assistant
```

**Preconditions**: None

**Postconditions**:
- Returns new existing Assistant message with identical content
- Discards finishReason and metaInfo (not in existing type)
- Original Koog message unchanged

**Error Conditions**: None

**Complexity**: O(1) - direct field copy

---

### Contract: `Message.ToolCall.toKoog()`

**Signature**:
```kotlin
context(_: Raise<ConversionError>)
fun predictable.agent.Message.ToolCall.toKoog(
    metaInfo: ai.koog.prompt.message.ResponseMetaInfo
): ai.koog.prompt.message.Message.Tool.Call
```

**Preconditions**:
- `name` must not be blank
- `metaInfo` must be valid ResponseMetaInfo instance

**Postconditions**:
- Returns new Koog Tool.Call message with:
  - id from existing message
  - tool = name
  - content = arguments
  - provided metaInfo
- Original message unchanged

**Error Conditions**:
- Raises `ConversionError.EmptyToolName` if name is blank

**Complexity**: O(1) - direct field mapping

---

### Contract: `Message.Tool.Call.fromKoog()`

**Signature**:
```kotlin
context(_: Raise<ConversionError>)
fun ai.koog.prompt.message.Message.Tool.Call.fromKoog(): predictable.agent.Message.ToolCall
```

**Preconditions**:
- `id` must not be null (required by existing type)

**Postconditions**:
- Returns new existing ToolCall message with:
  - id from Koog message
  - name = tool
  - arguments = content
- Original Koog message unchanged

**Error Conditions**:
- Raises `ConversionError.MissingToolCallId` if id is null

**Complexity**: O(1) - direct field mapping with null check

---

### Contract: `Message.ToolResult.toKoog()`

**Signature**:
```kotlin
fun predictable.agent.Message.ToolResult.toKoog(): ai.koog.prompt.message.Message.Tool.Result
```

**Preconditions**: None (id guaranteed non-null by existing type)

**Postconditions**:
- Returns new Koog Tool.Result message with identical id and content
- Original message unchanged

**Error Conditions**: None

**Complexity**: O(1) - direct field copy

---

### Contract: `Message.Tool.Result.fromKoog()`

**Signature**:
```kotlin
context(_: Raise<ConversionError>)
fun ai.koog.prompt.message.Message.Tool.Result.fromKoog(): predictable.agent.Message.ToolResult
```

**Preconditions**:
- `id` must not be null (required by existing type)

**Postconditions**:
- Returns new existing ToolResult message with identical id and content
- Original Koog message unchanged

**Error Conditions**:
- Raises `ConversionError.MissingToolResultId` if id is null

**Complexity**: O(1) - direct field copy with null check

---

## Model Adapters

### Contract: `Model.toKoog()`

**Signature**:
```kotlin
context(_: Raise<ConversionError>)
fun predictable.agent.Model.toKoog(): ai.koog.prompt.llm.LLModel
```

**Preconditions**:
- `name` must correspond to known OpenAI model

**Postconditions**:
- Returns LLModel instance from Koog's OpenAIModels catalog
- Lookup is case-insensitive
- Original Model unchanged

**Error Conditions**:
- Raises `ConversionError.UnknownModel(name)` if model not found

**Supported Models**:
- "gpt-4o" → OpenAIModels.Chat.GPT4o
- "gpt-4o-mini" → OpenAIModels.Chat.GPT4oMini
- "gpt-4-turbo" → OpenAIModels.Chat.GPT4Turbo
- "gpt-4" → OpenAIModels.Chat.GPT4
- "gpt-3.5-turbo" → OpenAIModels.Chat.GPT35Turbo

**Complexity**: O(1) - dictionary lookup (when expression)

---

### Contract: `LLModel.fromKoog()`

**Signature**:
```kotlin
fun ai.koog.prompt.llm.LLModel.fromKoog(): predictable.agent.Model
```

**Preconditions**: None

**Postconditions**:
- Returns new Model with name = LLModel.id
- Original LLModel unchanged

**Error Conditions**: None

**Complexity**: O(1) - direct field access

---

## Parameter Adapters

### Contract: `RequestParameters.toKoog()`

**Signature**:
```kotlin
context(_: Raise<ConversionError>)
fun predictable.agent.RequestParameters.toKoog(): ai.koog.prompt.params.LLMParams
```

**Preconditions**:
- If `temperature` present: must be in [0.0, 2.0]
- If `topP` present: must be in [0.0, 1.0]
- If `maxTokens` present: must be > 0
- If `frequencyPenalty` present: must be in [-2.0, 2.0]
- If `presencePenalty` present: must be in [-2.0, 2.0]

**Postconditions**:
- Returns new LLMParams with mapped fields
- Null fields remain null (optional parameters)
- Original RequestParameters unchanged

**Error Conditions**:
- Raises `ConversionError.InvalidTemperature(value)` if out of range
- Raises `ConversionError.InvalidTopP(value)` if out of range
- Raises `ConversionError.InvalidMaxTokens(value)` if ≤ 0

**Complexity**: O(1) - direct field copy with validation

---

### Contract: `LLMParams.fromKoog()`

**Signature**:
```kotlin
fun ai.koog.prompt.params.LLMParams.fromKoog(): predictable.agent.RequestParameters
```

**Preconditions**: None

**Postconditions**:
- Returns new RequestParameters with core fields mapped
- Extra Koog-specific fields discarded (lossy conversion)
- Original LLMParams unchanged

**Error Conditions**: None

**Complexity**: O(1) - direct field copy

---

## Tool Adapters

### Contract: `AI.toKoogToolDescriptor()`

**Signature**:
```kotlin
fun predictable.AI<*, *>.toKoogToolDescriptor(): ai.koog.agents.core.tools.ToolDescriptor
```

**Preconditions**: None (AI instance already valid)

**Postconditions**:
- Returns ToolDescriptor object wrapping the AI instance
- ToolDescriptor delegates to AI methods
- Original AI unchanged

**Error Conditions**: None

**Complexity**: O(1) - object wrapper creation

**Implementation Note**: Creates anonymous object implementing ToolDescriptor

---

## Response Adapters

### Contract: `List<Message.Response>.toAgentResponseText()`

**Signature**:
```kotlin
context(_: Raise<ConversionError>)
fun List<ai.koog.prompt.message.Message.Response>.toAgentResponseText(
    modelId: String
): predictable.agent.AgentResponse.Text
```

**Preconditions**:
- List must not be empty
- List must contain at least one Assistant message

**Postconditions**:
- Returns AgentResponse.Text with:
  - value = last Assistant message content
  - metadata = converted from ResponseMetaInfo
  - messages = all converted messages
- Original Koog messages unchanged

**Error Conditions**:
- Raises `ConversionError.EmptyResponses` if list is empty
- Raises `ConversionError.NoAssistantResponse` if no Assistant message found

**Complexity**: O(n) where n = number of messages (for filtering and mapping)

---

### Contract: `List<Message.Response>.toAgentResponseStructured()`

**Signature**:
```kotlin
context(_: Raise<ConversionError>)
fun <T> List<ai.koog.prompt.message.Message.Response>.toAgentResponseStructured(
    schema: predictable.tool.OutputSchema<T>,
    modelId: String
): predictable.agent.AgentResponse.Structured<T>
```

**Preconditions**:
- List must not be empty
- List must contain at least one Assistant message
- Assistant message content must be valid JSON matching schema

**Postconditions**:
- Returns AgentResponse.Structured with:
  - value = parsed JSON as type T
  - metadata = converted from ResponseMetaInfo
  - messages = all converted messages
- Original Koog messages unchanged

**Error Conditions**:
- Raises `ConversionError.EmptyResponses` if list is empty
- Raises `ConversionError.NoAssistantResponse` if no Assistant message found
- Raises `ConversionError.EmptyStructuredContent` if content is null/empty
- Raises `ConversionError.JsonParsingFailed(error)` if JSON parsing fails

**Complexity**: O(n) + O(JSON parsing) where n = number of messages

---

### Contract: `Flow<StreamFrame>.toStringStream()`

**Signature**:
```kotlin
fun Flow<ai.koog.prompt.streaming.StreamFrame>.toStringStream(): Flow<String>
```

**Preconditions**: None

**Postconditions**:
- Returns Flow emitting only text from Append frames
- End and ToolCall frames filtered out (emit null)
- Original Flow unchanged (transformation creates new Flow)

**Error Conditions**: None (Flow transformation cannot fail at creation time)

**Complexity**: O(1) for transformation setup, O(n) at collection time where n = number of frames

---

### Contract: `Flow<StreamFrame>.toAgentResponseStringStream()`

**Signature**:
```kotlin
fun Flow<ai.koog.prompt.streaming.StreamFrame>.toAgentResponseStringStream():
    predictable.agent.AgentResponse.StringStream
```

**Preconditions**: None

**Postconditions**:
- Returns AgentResponse.StringStream wrapping transformed Flow
- Original Flow unchanged

**Error Conditions**: None

**Complexity**: O(1) - wrapper creation + Flow transformation

---

## Metadata Adapters

### Contract: `ResponseMetaInfo.toUsage()`

**Signature**:
```kotlin
fun ai.koog.prompt.message.ResponseMetaInfo.toUsage(): predictable.agent.Usage
```

**Preconditions**: None

**Postconditions**:
- Returns Usage with token counts mapped:
  - promptTokens = inputTokensCount ?? 0
  - completionTokens = outputTokensCount ?? 0
  - totalTokens = totalTokensCount ?? 0
- Null values treated as 0
- Original ResponseMetaInfo unchanged

**Error Conditions**: None

**Complexity**: O(1) - direct field mapping

---

### Contract: `createAgentMetadata()`

**Signature**:
```kotlin
fun createAgentMetadata(
    metaInfo: ai.koog.prompt.message.ResponseMetaInfo,
    modelId: String
): predictable.agent.AgentMetadata
```

**Preconditions**: None

**Postconditions**:
- Returns AgentMetadata with:
  - usage = converted from metaInfo
  - model = provided modelId
- Original metaInfo unchanged

**Error Conditions**: None

**Complexity**: O(1) - calls toUsage() + constructor

---

## Batch Adapters

### Contract: `List<Message>.toKoogMessages()`

**Signature**:
```kotlin
context(_: Raise<ConversionError>)
fun List<predictable.agent.Message>.toKoogMessages(): List<ai.koog.prompt.message.Message>
```

**Preconditions**: Each message must be valid for conversion

**Postconditions**:
- Returns new list with all messages converted to Koog types
- Original list unchanged
- Order preserved

**Error Conditions**:
- Raises any ConversionError from individual message conversion
- Short-circuits on first error (fail-fast)

**Complexity**: O(n) where n = number of messages

---

### Contract: `List<AI<*, *>>.toKoogToolDescriptors()`

**Signature**:
```kotlin
fun List<predictable.AI<*, *>>.toKoogToolDescriptors():
    List<ai.koog.agents.core.tools.ToolDescriptor>
```

**Preconditions**: None

**Postconditions**:
- Returns new list with all AI instances wrapped as ToolDescriptors
- Original list unchanged
- Order preserved

**Error Conditions**: None

**Complexity**: O(n) where n = number of tools

---

## Contract Summary Table

| Adapter | Preconditions | Postconditions | Error Conditions | Complexity |
|---------|---------------|----------------|------------------|------------|
| `Message.System.toKoog()` | content not blank | New Koog System message | EmptyContent | O(1) |
| `Message.System.fromKoog()` | None | New existing System message | None | O(1) |
| `Message.User.toKoog()` | content not blank | New Koog User message | EmptyContent | O(1) |
| `Message.User.fromKoog()` | None | New existing User message | None | O(1) |
| `Message.Assistant.toKoog()` | Valid metaInfo | New Koog Assistant message | None | O(1) |
| `Message.Assistant.fromKoog()` | None | New existing Assistant message | None | O(1) |
| `Message.ToolCall.toKoog()` | name not blank | New Koog Tool.Call | EmptyToolName | O(1) |
| `Message.Tool.Call.fromKoog()` | id not null | New existing ToolCall | MissingToolCallId | O(1) |
| `Message.ToolResult.toKoog()` | None | New Koog Tool.Result | None | O(1) |
| `Message.Tool.Result.fromKoog()` | id not null | New existing ToolResult | MissingToolResultId | O(1) |
| `Model.toKoog()` | Known model name | LLModel from catalog | UnknownModel | O(1) |
| `LLModel.fromKoog()` | None | New Model with id | None | O(1) |
| `RequestParameters.toKoog()` | Valid ranges | New LLMParams | Invalid* errors | O(1) |
| `LLMParams.fromKoog()` | None | New RequestParameters (lossy) | None | O(1) |
| `AI.toKoogToolDescriptor()` | None | ToolDescriptor wrapper | None | O(1) |
| `List<Response>.toAgentResponseText()` | Non-empty, has Assistant | AgentResponse.Text | Empty*, NoAssistant | O(n) |
| `List<Response>.toAgentResponseStructured()` | Valid JSON content | AgentResponse.Structured<T> | Empty*, NoAssistant, JsonParsingFailed | O(n) |
| `Flow<StreamFrame>.toStringStream()` | None | Flow<String> | None | O(1) setup |
| `ResponseMetaInfo.toUsage()` | None | Usage with token counts | None | O(1) |

---

## Constitutional Compliance

All adapter contracts comply with:

1. **Function Size**: All implementations ≤10 lines (enforced by decomposition)
2. **Immutability**: All conversions create new instances, never mutate
3. **Arrow Raise**: All fallible conversions use `context(_: Raise<ConversionError>)`
4. **Type Safety**: No `any` types, explicit error types
5. **No Exceptions**: Use Raise context, not try-catch (except wrapping Koog exceptions)

---

## Testing Requirements

Each adapter contract MUST have:

1. **Success Path Test**: Valid input → correct output
2. **Error Path Tests**: Each error condition → correct error raised
3. **Immutability Test**: Original unchanged after conversion
4. **Bidirectional Test**: `toKoog().fromKoog()` preserves data (where applicable)
5. **Null Handling Test**: Optional fields handled correctly

---

## References

- **Data Model**: `/Users/raulraja/predictable-machines/predictable-agents/specs/001-migrate-openai-to-koog/data-model.md`
- **Research**: `/Users/raulraja/predictable-machines/predictable-agents/specs/001-migrate-openai-to-koog/research.md`
- **Constitution**: `/Users/raulraja/predictable-machines/predictable-agents/.specify/memory/constitution.md` (§ Error Handling, § Function Size Discipline)
