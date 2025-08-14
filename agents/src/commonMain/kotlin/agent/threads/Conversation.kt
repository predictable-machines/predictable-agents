package predictable.agent.threads

import predictable.agent.Message

interface Conversation {
  val id: String
  val name: String
  val description: String
  suspend fun messages(limit: Int): List<Message>
}


