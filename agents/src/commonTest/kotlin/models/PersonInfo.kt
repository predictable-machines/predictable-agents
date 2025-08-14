package predictable.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("PersonInfo")
data class PersonInfo(
    val name: String,
    val age: Int,
    val occupation: String
)
