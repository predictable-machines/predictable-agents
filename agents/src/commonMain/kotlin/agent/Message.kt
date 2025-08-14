package predictable.agent

import kotlinx.serialization.Serializable
import predictable.tool.ToolCallRequest

@Serializable
data class Message(
  val role: MessageRole,
  val content: String,
  val name: String? = null,
  val toolCalls: List<ToolCallRequest>? = null,
  val toolCallId: String? = null,
) {
  companion object {
    fun system(content: String): Message = Message(MessageRole.System, content)
    fun assistant(content: String): Message = Message(MessageRole.Assistant, content)
    fun user(content: String): Message = Message(MessageRole.User, content)
    fun custom(role: String, content: String): Message = Message(MessageRole.Custom(role), content)
  }
}
