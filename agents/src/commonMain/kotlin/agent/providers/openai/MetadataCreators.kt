package predictable.agent.providers.openai

import com.aallam.openai.api.core.Usage
import com.aallam.openai.api.model.ModelId
import predictable.agent.AgentMetadata

/**
 * Creates an AgentMetadata object with the given parameters
 */
fun createAgentMetadata(usage: Usage?, modelId: ModelId): AgentMetadata {
    return AgentMetadata(
        promptTokens = usage?.promptTokens ?: 0,
        completionTokens = usage?.completionTokens ?: 0,
        totalTokens = usage?.totalTokens ?: 0,
        model = modelId.id,
        provider = "openai"
    )
}
