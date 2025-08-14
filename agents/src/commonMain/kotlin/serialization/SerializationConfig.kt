package predictable.serialization

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import predictable.tool.KotlinSchema

class SerializationConfig<A>(
  val serialize: (A) -> JsonObject,
  val deserialize: (String) -> A,
) {
  companion object {
    inline fun <reified A> default() = SerializationConfig<A>({
      KotlinSchema.Companion.json.encodeToJsonElement(it).jsonObject
    }, {
      KotlinSchema.Companion.json.decodeFromString(it)
    })
  }
}
