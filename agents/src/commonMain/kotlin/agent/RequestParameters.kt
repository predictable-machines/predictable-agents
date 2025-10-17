package predictable.agent

import kotlinx.serialization.Serializable
import predictable.tool.ToolChoice
import kotlin.jvm.JvmField
import kotlin.jvm.JvmOverloads

/**
 * Configuration parameters for AI model requests.
 * 
 * This data class encapsulates all the parameters that can be used to control
 * the behavior of AI model generation, including temperature, token limits,
 * penalties, and tool usage settings. All parameters are optional with sensible defaults.
 *
 * @property temperature Controls randomness in generation (0.0 = deterministic, 2.0 = very random)
 * @property topP Nucleus sampling parameter (0.0 to 1.0)
 * @property n Number of completions to generate
 * @property stop List of sequences where the model should stop generating
 * @property store Whether to store this interaction for training/analysis
 * @property maxCompletionTokens Maximum tokens to generate in the response
 * @property presencePenalty Penalty for repeating topics (-2.0 to 2.0)
 * @property frequencyPenalty Penalty for repeating tokens (-2.0 to 2.0)
 * @property logitBias Map of token IDs to bias values for controlling specific tokens
 * @property user Unique identifier for the end-user
 * @property toolChoice Strategy for tool usage (Auto, None, Required, or specific tool)
 * @property logprobs Whether to return log probabilities of tokens
 * @property topLogprobs Number of most likely tokens to return log probabilities for
 * @property instanceId Unique identifier for this request instance
 * @property maxSteps Maximum number of steps for multi-step operations
 * @property compressionStrategy Strategy for compressing conversation history (WholeHistory, LastNMessages, etc.)
 * @property maxHistorySize Maximum number of messages to keep in history
 * @property maxTokens Maximum estimated token count for the entire prompt
 * @property enableTokenCaching Enable caching for token count estimation
 */
@Serializable
data class RequestParameters @JvmOverloads constructor(
  val temperature: Double? = null,
  val topP: Double? = null,
  val n: Int? = null,
  val stop: List<String>? = null,
  val store: Boolean? = null,
  val maxCompletionTokens: Int? = null,
  val presencePenalty: Double? = null,
  val frequencyPenalty: Double? = null,
  val logitBias: Map<String, Int>? = null,
  val user: String? = null,
  val toolChoice: ToolChoice = ToolChoice.Auto,
  val logprobs: Boolean? = null,
  val topLogprobs: Int? = null,
  val instanceId: String? = null,
  val maxSteps: Int = MAX_STEPS,
  val compressionStrategy: CompressionStrategy? = null,
  val maxHistorySize: Int? = null,
  val maxTokens: Int? = null,
  val enableTokenCaching: Boolean = true,
) {
  companion object {
    /**
     * Default maximum number of steps for multi-step operations.
     */
    const val MAX_STEPS = 100
    
    /**
     * Default request parameters with all optional values set to null.
     * Uses automatic tool choice and default max steps.
     */
    @JvmField
    val defaultParameters: RequestParameters = RequestParameters()
  }
}
