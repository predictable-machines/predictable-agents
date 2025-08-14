package predictable.agent

import kotlinx.serialization.Serializable
import predictable.tool.ToolChoice

@Serializable
data class RequestParameters(
  val reasoningEffort: Effort? = null,
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
) {
  companion object {
    const val MAX_STEPS = 100
    val default: RequestParameters = RequestParameters()
  }
}
