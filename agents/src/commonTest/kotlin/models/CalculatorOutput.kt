package predictable.models

import kotlinx.serialization.Serializable

// Define a simple data class for our tool output
@Serializable
data class CalculatorOutput(val result: Int)
