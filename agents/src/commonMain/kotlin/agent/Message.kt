package predictable.agent

import kotlinx.serialization.Serializable
import predictable.tool.ToolCallRequest

/**
 * Represents a message in a conversation between users, assistants, and systems.
 * 
 * Messages form the core communication unit in agent interactions, supporting
 * various roles (user, assistant, system, custom) and optionally including
 * tool call information for function execution.
 * 
 * @property role The role of the message sender (user, assistant, system, or custom)
 * @property content The text content of the message
 * @property name Optional name identifier for the message sender
 * @property toolCalls List of tool call requests if this message triggers tool execution
 * @property toolCallId Identifier for tool call responses
 */
@Serializable
data class Message(
  val role: MessageRole,
  val content: String,
  val name: String? = null,
  val toolCalls: List<ToolCallRequest>? = null,
  val toolCallId: String? = null,
) {
  companion object {
    /**
     * Creates a system message.
     * System messages typically contain instructions or context for the AI.
     * 
     * @param content The system message content
     * @return A Message with System role
     */
    fun system(content: String): Message = Message(MessageRole.System, content)
    
    /**
     * Creates an assistant message.
     * Assistant messages represent AI-generated responses.
     * 
     * @param content The assistant's response content
     * @return A Message with Assistant role
     */
    fun assistant(content: String): Message = Message(MessageRole.Assistant, content)
    
    /**
     * Creates a user message.
     * User messages represent input from the human user.
     * 
     * @param content The user's message content
     * @return A Message with User role
     */
    fun user(content: String): Message = Message(MessageRole.User, content)
    
    /**
     * Creates a message with a custom role.
     * Allows for extensibility with non-standard roles.
     * 
     * @param role The custom role identifier
     * @param content The message content
     * @return A Message with Custom role
     */
    fun custom(role: String, content: String): Message = Message(MessageRole.Custom(role), content)
  }
}
