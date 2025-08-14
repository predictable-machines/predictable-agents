package predictable.models

import com.xemantic.ai.tool.schema.meta.Description
import kotlinx.serialization.Required
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("WeatherForecastRequest")
@Description("IMPORTANT! All fields required")
data class WeatherForecastRequest(
    @Required
    val location: String
)
