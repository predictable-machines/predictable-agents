package predictable.models

import com.xemantic.ai.tool.schema.meta.Description
import kotlinx.serialization.Required
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("CalculationResponse")
@Description("IMPORTANT! All fields required")
data class CalculationResponse(
    @Required
    val question: String,
    @Required
    val answer: Int,
    @Required
    val explanation: String
)
