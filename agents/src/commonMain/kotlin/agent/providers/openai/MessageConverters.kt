package predictable.agent.providers.openai

import com.aallam.openai.api.chat.*
import predictable.agent.*
import predictable.tool.ToolCallRequest

fun convertMessageToChatMessage(message: Message): ChatMessage =
    when (message.role) {
        is MessageRole.User -> ChatMessage(role = ChatRole.User, content = message.content, name = message.name)
        is MessageRole.Custom -> ChatMessage(role = ChatRole.User, content = message.content, name = message.name)
        MessageRole.System -> ChatMessage(role = ChatRole.System, content = message.content, name = message.name)

        is MessageRole.Assistant -> ChatMessage(
            role = ChatRole.Assistant,
            content = message.content,
            name = message.name,
            toolCalls = message.toolCalls?.map { toolCall ->
                val functionCall = FunctionCall(toolCall.name, toolCall.arguments)
                ToolCall.Function(ToolId(toolCall.id), functionCall)
            }
        )

        MessageRole.ToolResult -> ChatMessage(
            role = ChatRole.Tool,
            content = message.content,
            name = message.name,
            toolCallId = message.toolCallId?.let { ToolId(it) }
        )
    }

fun convertChatMessageToMessage(chatMessage: ChatMessage): Message {
    val role = when (chatMessage.role) {
        ChatRole.User -> MessageRole.User
        ChatRole.Assistant -> MessageRole.Assistant
        ChatRole.System -> MessageRole.System
        ChatRole.Tool -> MessageRole.ToolResult
        else -> MessageRole.Custom(chatMessage.role.role)
    }

    val toolCalls = chatMessage.toolCalls
        ?.filterIsInstance<ToolCall.Function>()
        ?.map { toolCall ->
            ToolCallRequest(
                id = toolCall.id.id,
                name = toolCall.function.name,
                arguments = toolCall.function.arguments
            )
        }

    return Message(
        role = role,
        content = chatMessage.content.orEmpty(),
        name = chatMessage.name,
        toolCalls = toolCalls,
        toolCallId = chatMessage.toolCallId?.id
    )
}

fun List<Message>.toOpenAIChatMessages(): List<ChatMessage> =
    map { message -> convertMessageToChatMessage(message) }
