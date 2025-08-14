package predictable.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("WeatherResponse")
data class WeatherResponse(
    val location: String,
    val temperature: Double,
    val conditions: String,
    val unit: String
)
