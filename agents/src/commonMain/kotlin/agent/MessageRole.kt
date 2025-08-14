package predictable.agent

import kotlinx.serialization.Serializable

@Serializable
sealed class MessageRole {
  data object User : MessageRole()
  data object Assistant : MessageRole()
  data object System : MessageRole()
  data object ToolResult : MessageRole()
  data class Custom(val value: String) : MessageRole()
}
