package predictable.models

import kotlinx.serialization.Serializable

// Define simple data classes for our tool inputs (reusing from AgentToolsTest)
@Serializable
data class MultiStepCalculatorInput(val operation: String, val a: Int, val b: Int)
