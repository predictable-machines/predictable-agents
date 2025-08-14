package predictable.agent

import kotlinx.serialization.Serializable
import predictable.tool.ToolCallRequest
import predictable.tool.ToolCallResponse

@Serializable
sealed class StreamResponse<out T> {

  @Serializable
  data class Chunk<out T>(val value: T): StreamResponse<T>()

  @Serializable
  data class Metadata(val value: AgentMetadata): StreamResponse<Nothing>()

  @Serializable
  data class ToolCall(val value: ToolCallRequest): StreamResponse<Nothing>()

  @Serializable
  data class ToolResult(val value: ToolCallResponse): StreamResponse<Nothing>()

  @Serializable
  data object End: StreamResponse<Nothing>()
}
