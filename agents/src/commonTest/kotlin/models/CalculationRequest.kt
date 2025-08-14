package predictable.models

import com.xemantic.ai.tool.schema.meta.Description
import kotlinx.serialization.Required
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Define data classes for structured output with tools testing
@Serializable
@SerialName("CalculationRequest")
@Description("IMPORTANT! All fields required")
data class CalculationRequest(
    @Required
    val question: String
)
