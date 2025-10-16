package predictable.agent

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmField

/**
 * Configuration for an AI model endpoint.
 *
 * Specifies the API endpoint and model name to use for AI operations.
 * Supports various providers including OpenAI, OpenRouter, and local models.
 *
 * @property apiUrl The base URL for the AI provider's API
 * @property name The specific model identifier to use (e.g., "gpt-4", "claude-3")
 */
@Serializable
data class Model(
  val apiUrl: String,
  val name: String
) {
  companion object {
    /**
     * The standard OpenAI API base URL.
     */
    @JvmField
    val openAIBaseUrl = "https://api.openai.com"
    
    /**
     * Default model configuration.
     * Uses GPT-4.1-nano by default for cost-effective operations.
     */
    @JvmField
    val defaultModel: Model =
//      Model(
//        apiUrl = "http://localhost:11434/v1/",
//        name = "qwen3"
//      )
      Model(openAIBaseUrl, "gpt-4.1-nano")

    /**
     * Model configuration for verification tasks.
     * Uses a lightweight model suitable for validation and checks.
     */
    @JvmField
    val verification: Model =
      Model(openAIBaseUrl, "gpt-4.1-nano")
  }
}
