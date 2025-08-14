package predictable.models

import kotlinx.serialization.Serializable

@Serializable
data class WeatherInput(val query: String)
