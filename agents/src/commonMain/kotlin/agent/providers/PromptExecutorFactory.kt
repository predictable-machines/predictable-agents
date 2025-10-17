package predictable.agent.providers

import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.google.GoogleLLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import predictable.agent.Model

/**
 * Factory for creating LLMClient instances based on API URL.
 *
 * Infers the provider from the apiUrl and creates the appropriate client.
 * Supports: OpenAI, Anthropic, Google, and OpenAI-compatible APIs (OpenRouter, local models).
 */
object ClientFactory {
  fun create(model: Model, apiKey: String): LLMClient {
    val url = model.apiUrl.lowercase()
    return when {
      url.contains("anthropic") -> createAnthropic(apiKey)
      url.contains("googleapis") || url.contains("generativelanguage") -> createGoogle(apiKey)
      else -> createOpenAI(model, apiKey)
    }
  }

  private fun createOpenAI(model: Model, apiKey: String): OpenAILLMClient {
    val settings = OpenAIClientSettings(baseUrl = model.apiUrl)
    return OpenAILLMClient(apiKey = apiKey, settings = settings)
  }

  private fun createAnthropic(apiKey: String): AnthropicLLMClient =
    AnthropicLLMClient(apiKey = apiKey)

  private fun createGoogle(apiKey: String): GoogleLLMClient =
    GoogleLLMClient(apiKey = apiKey)
}
