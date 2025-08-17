package predictable.agent

import kotlinx.serialization.Serializable
import predictable.tool.ToolCallRequest
import predictable.tool.ToolCallResponse

/**
 * Represents different types of events in a streaming response.
 * 
 * This sealed class provides type-safe handling of various events that can occur
 * during streaming AI generation, including content chunks, metadata, tool calls,
 * and stream termination.
 * 
 * @param T The type of content being streamed
 */
@Serializable
sealed class StreamResponse<out T> {

  /**
   * A chunk of generated content in the stream.
   * 
   * Contains partial results that accumulate to form the complete response.
   * 
   * @param T The type of content in this chunk
   * @property value The partial content of type [T]
   */
  @Serializable
  data class Chunk<out T>(val value: T): StreamResponse<T>()

  /**
   * Metadata about the streaming operation.
   * 
   * Contains information about token usage and model details,
   * typically sent at the end of the stream.
   * 
   * @property value The metadata containing usage statistics
   */
  @Serializable
  data class Metadata(val value: AgentMetadata): StreamResponse<Nothing>()

  /**
   * A tool call request during streaming.
   * 
   * Indicates that the AI wants to execute a tool/function
   * as part of generating the response.
   * 
   * @property value The tool call request details
   */
  @Serializable
  data class ToolCall(val value: ToolCallRequest): StreamResponse<Nothing>()

  /**
   * Result from a tool execution during streaming.
   * 
   * Contains the output from a previously requested tool call.
   * 
   * @property value The tool call response with results
   */
  @Serializable
  data class ToolResult(val value: ToolCallResponse): StreamResponse<Nothing>()

  /**
   * Signals the end of the stream.
   * 
   * Indicates that no more chunks will be emitted and
   * the streaming operation is complete.
   */
  @Serializable
  data object End: StreamResponse<Nothing>()
}
