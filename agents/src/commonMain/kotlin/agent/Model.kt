package predictable.agent

import com.aallam.openai.client.OpenAIHost
import kotlinx.serialization.Serializable

@Serializable
data class Model(
  val apiUrl: String,
  val name: String
) {
  companion object {
    val openAIBaseUrl = OpenAIHost.OpenAI.baseUrl
    val default: Model =
//      Model(
//        apiUrl = "http://localhost:11434/v1/",
//        name = "qwen3"
//      )
      Model(openAIBaseUrl, "gpt-4.1-nano")

    val verification: Model =
      Model(openAIBaseUrl, "gpt-4.1-nano")
  }
}
