package predictable.models

import com.xemantic.ai.tool.schema.meta.Description
import kotlinx.serialization.Required
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("WeatherForecastResponse")
@Description("IMPORTANT! All fields required")
data class WeatherForecastResponse(
    @Required
    val location: String,
    @Required
    val forecast: String,
    @Required
    val temperature: String
)
