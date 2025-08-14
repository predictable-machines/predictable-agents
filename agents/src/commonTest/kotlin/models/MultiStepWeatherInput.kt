package predictable.models

import kotlinx.serialization.Serializable

@Serializable
data class MultiStepWeatherInput(val query: String)
