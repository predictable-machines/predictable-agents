package predictable.models

import kotlinx.serialization.Serializable

// Define simple data classes for our tool inputs
@Serializable
data class CalculatorInput(val operation: Operation, val a: Int, val b: Int)
