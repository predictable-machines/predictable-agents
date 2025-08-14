package predictable.tool

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Data class for wrapping a list of elements in a JSON response
 */
@Serializable
@SerialName("Elements")
data class Elements<T>(
  val elements: List<T>
)
