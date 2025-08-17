package predictable.agent

import kotlinx.coroutines.flow.Flow

/**
 * Represents different types of responses that an Agent can produce.
 * 
 * This sealed interface provides type-safe abstractions for various response formats
 * including plain text, structured data, and streaming variants of both.
 * Each response type may include metadata about the generation process.
 */
sealed interface AgentResponse {
  /**
   * A text response with associated metadata.
   * 
   * Contains the generated text along with information about token usage
   * and the complete message history used for generation.
   * 
   * @property value The generated text response
   * @property metadata Information about token usage and model
   * @property messages The complete message history including the response
   */
  data class Text(val value: String, val metadata: AgentMetadata, val messages: List<Message>) : AgentResponse
  /**
   * A streaming text response.
   * 
   * Provides real-time text generation through a Flow of stream responses,
   * allowing for progressive display of generated content.
   * 
   * @property value Flow emitting incremental text chunks as they're generated
   */
  data class StringStream(val value: Flow<StreamResponse<String>>) : AgentResponse
  /**
   * A structured response with type-safe output.
   * 
   * Contains a generated object of type [T] with validation and metadata.
   * Useful for generating specific data structures like JSON objects or domain models.
   * 
   * @param T The type of the structured output
   * @property value The generated structured data of type [T]
   * @property metadata Information about token usage and model
   * @property messages The complete message history including the response
   */
  data class Structured<out T>(val value: T, val metadata: AgentMetadata, val messages: List<Message>) : AgentResponse
  /**
   * A streaming structured response.
   * 
   * Provides real-time generation of structured data through a Flow,
   * allowing for progressive construction of complex objects.
   * 
   * @param T The type of the structured output being streamed
   * @property value Flow emitting incremental updates to the structured data
   */
  data class StructuredStream<out T>(val value: Flow<StreamResponse<T>>) : AgentResponse
}
