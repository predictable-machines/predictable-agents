package predictable.agent

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmField

/**
 * AI provider types supported by the agent system.
 * Each provider has specific API requirements and authentication methods.
 */
@Serializable
sealed class Provider {
  @Serializable
  data object OpenAI : Provider()

  @Serializable
  data object Anthropic : Provider()

  @Serializable
  data object Google : Provider()

  @Serializable
  data object OpenRouter : Provider()

  @Serializable
  data object Ollama : Provider()

  /**
   * Returns the environment variable name for this provider's API key.
   */
  fun envKeyName(): String = when (this) {
    is OpenAI -> "OPENAI_API_KEY"
    is Anthropic -> "ANTHROPIC_API_KEY"
    is Google -> "GOOGLE_API_KEY"
    is OpenRouter -> "OPENROUTER_API_KEY"
    is Ollama -> ""
  }

  /**
   * Returns the provider name as a string for metadata purposes.
   */
  fun providerName(): String = when (this) {
    is OpenAI -> "openai"
    is Anthropic -> "anthropic"
    is Google -> "google"
    is OpenRouter -> "openrouter"
    is Ollama -> "ollama"
  }
}

/**
 * Configuration for an AI model endpoint.
 *
 * Specifies the API endpoint, model name, and provider type for AI operations.
 * Supports various providers including OpenAI, Anthropic, Google, OpenRouter, and local models.
 *
 * @property apiUrl The base URL for the AI provider's API
 * @property name The specific model identifier to use (e.g., "gpt-4", "claude-3")
 * @property provider The AI provider type (must be explicitly specified)
 */
@Serializable
data class Model(
  val apiUrl: String,
  val name: String,
  val provider: Provider
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
//        name = "qwen3",
//        provider = Provider.Ollama
//      )
      Model(openAIBaseUrl, "gpt-4.1-nano", Provider.OpenAI)

    /**
     * Model configuration for verification tasks.
     * Uses a lightweight model suitable for validation and checks.
     */
    @JvmField
    val verification: Model =
      Model(openAIBaseUrl, "gpt-4.1-nano", Provider.OpenAI)
  }
}
