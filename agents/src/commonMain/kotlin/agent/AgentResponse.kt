package predictable.agent

import kotlinx.coroutines.flow.Flow

sealed interface AgentResponse {
  data class Simple(val value: String, val metadata: AgentMetadata, val messages: List<Message>) : AgentResponse
  data class StringStream(val value: Flow<StreamResponse<String>>, val messages: List<Message>) : AgentResponse
  data class Structured<out T>(val value: T, val metadata: AgentMetadata, val messages: List<Message>) : AgentResponse
  data class StructuredStream<out T>(val value: Flow<StreamResponse<T>>, val messages: List<Message>) : AgentResponse
}
