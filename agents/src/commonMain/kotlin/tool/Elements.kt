package predictable.tool

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Container for multiple elements in structured responses.
 * 
 * Used to wrap collections of items when the AI needs to generate
 * multiple structured outputs or when streaming partial results.
 * Provides proper JSON structure for list-based responses.
 * 
 * @param T The type of elements in the collection
 * @property elements The list of elements of type [T]
 */
@Serializable
@SerialName("Elements")
data class Elements<T>(
  val elements: List<T>
)
