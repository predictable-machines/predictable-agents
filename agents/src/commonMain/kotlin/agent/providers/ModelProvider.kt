package predictable.agent.providers

import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import predictable.agent.Model

/**
 * Model provider that maps model names to Koog LLModel instances.
 *
 * Uses Koog's predefined model constants when available, or creates custom
 * instances for unknown models.
 */
object ModelProvider {
  fun fromModel(model: Model): LLModel = when (model.name.lowercase()) {
    // OpenAI models
    "gpt-4o" -> OpenAIModels.Chat.GPT4o
    "gpt-4.1" -> OpenAIModels.Chat.GPT4_1
    "gpt-4.1-nano" -> OpenAIModels.CostOptimized.GPT4_1Nano
    "gpt-4.1-mini" -> OpenAIModels.CostOptimized.GPT4_1Mini
    "gpt-4o-mini" -> OpenAIModels.CostOptimized.GPT4oMini
    "gpt-5" -> OpenAIModels.Chat.GPT5
    "gpt-5-mini" -> OpenAIModels.Chat.GPT5Mini
    "gpt-5-nano" -> OpenAIModels.Chat.GPT5Nano

    // Anthropic models
    "claude-3-opus", "claude-3-opus-20240229" -> AnthropicModels.Opus_3
    "claude-3-haiku", "claude-3-haiku-20240307" -> AnthropicModels.Haiku_3
    "claude-3-5-sonnet", "claude-3-5-sonnet-20241022" -> AnthropicModels.Sonnet_3_5
    "claude-3-5-haiku", "claude-3-5-haiku-20241022" -> AnthropicModels.Haiku_3_5
    "claude-3-7-sonnet", "claude-3-7-sonnet-20250219" -> AnthropicModels.Sonnet_3_7
    "claude-sonnet-4", "claude-sonnet-4-20250514" -> AnthropicModels.Sonnet_4
    "claude-opus-4", "claude-opus-4-20250514" -> AnthropicModels.Opus_4
    "claude-sonnet-4-5", "claude-sonnet-4-5-20250929" -> AnthropicModels.Sonnet_4_5

    // Google models
    "gemini-2.0-flash", "gemini-2.0-flash-exp" -> GoogleModels.Gemini2_0Flash
    "gemini-2.5-flash" -> GoogleModels.Gemini2_5Flash
    "gemini-2.5-pro" -> GoogleModels.Gemini2_5Pro

    // For any other model, create a custom instance
    else -> createCustomModel(model.name)
  }

  private fun createCustomModel(modelName: String): LLModel {
    val provider = inferProvider(modelName)
    return LLModel(
      provider = provider,
      id = modelName,
      capabilities = listOf(
        LLMCapability.Temperature,
        LLMCapability.Tools,
        LLMCapability.ToolChoice,
        LLMCapability.Completion
      ),
      contextLength = 128_000,
      maxOutputTokens = 4_096
    )
  }

  private fun inferProvider(modelName: String): LLMProvider = when {
    modelName.startsWith("gpt-") -> LLMProvider.OpenAI
    modelName.startsWith("claude-") -> LLMProvider.Anthropic
    modelName.startsWith("gemini-") -> LLMProvider.Google
    modelName.startsWith("llama") || modelName.startsWith("mistral") ||
    modelName.startsWith("qwen") || modelName.startsWith("deepseek") -> LLMProvider.Ollama
    else -> LLMProvider.OpenAI  // Default for unknown/custom models
  }
}
