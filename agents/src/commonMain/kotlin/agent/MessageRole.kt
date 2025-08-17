package predictable.agent

import kotlinx.serialization.Serializable

/**
 * Represents the role of a message sender in a conversation.
 * 
 * This sealed class defines the different participants in an AI conversation,
 * ensuring type-safe handling of message origins and appropriate processing
 * based on the sender's role.
 */
@Serializable
sealed class MessageRole {
  /**
   * Represents a human user in the conversation.
   * User messages typically contain questions, requests, or input data.
   */
  data object User : MessageRole()
  /**
   * Represents the AI assistant in the conversation.
   * Assistant messages contain AI-generated responses and outputs.
   */
  data object Assistant : MessageRole()
  /**
   * Represents system-level instructions or context.
   * System messages define the AI's behavior, personality, and constraints.
   */
  data object System : MessageRole()
  /**
   * Represents the result from a tool execution.
   * Tool result messages contain output from function calls or external tools.
   */
  data object ToolResult : MessageRole()
  /**
   * Represents a custom role not covered by standard roles.
   * Allows for extensibility and domain-specific message types.
   * 
   * @property value The custom role identifier
   */
  data class Custom(val value: String) : MessageRole()
}
