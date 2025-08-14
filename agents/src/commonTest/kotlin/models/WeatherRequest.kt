package predictable.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Define simple data classes for structured output testing
@Serializable
@SerialName("WeatherRequest")
data class WeatherRequest(val location: String, val unit: String = "celsius")
