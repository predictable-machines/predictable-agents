package predictable.agent.providers.openai

import com.aallam.openai.api.core.Usage

/**
 * Immutable accumulator for usage statistics across multiple API calls.
 * This is used to track the total token usage across all tool call rounds
 * and intermediate responses in a conversation.
 */
data class AccumulatedUsage(
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val totalTokens: Int = 0
) {
    /**
     * Operator overload for adding usage using the + operator.
     * Returns a new AccumulatedUsage instance with the given usage added.
     * Safely handles null values.
     * This is a pure function that doesn't mutate the original instance.
     * Example: val newUsage = accumulatedUsage + apiUsage
     */
    operator fun plus(usage: Usage?): AccumulatedUsage {
        return if (usage == null) {
            this
        } else {
            copy(
                promptTokens = promptTokens + (usage.promptTokens ?: 0),
                completionTokens = completionTokens + (usage.completionTokens ?: 0),
                totalTokens = totalTokens + (usage.totalTokens ?: 0)
            )
        }
    }

    /**
     * Creates an OpenAI Usage object from the accumulated values.
     */
    fun toUsage(): Usage = Usage(
        promptTokens = promptTokens,
        completionTokens = completionTokens,
        totalTokens = totalTokens
    )
    
    companion object {
        /**
         * Combines multiple Usage objects into a single AccumulatedUsage.
         * This is a pure function that creates a new instance.
         */
        fun fromUsages(vararg usages: Usage?): AccumulatedUsage {
            return usages.fold(AccumulatedUsage()) { acc, usage ->
                acc + usage
            }
        }
    }
}