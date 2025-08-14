@file:OptIn(ExperimentalSerializationApi::class)

@file:UseSerializers(
  EitherSerializer::class,
  IorSerializer::class,
  OptionSerializer::class,
  NonEmptyListSerializer::class,
  NonEmptySetSerializer::class
)

package predictable.tool
import arrow.core.serialization.*
import com.xemantic.ai.tool.schema.generator.generateSchema
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.json.*

interface InputSchema<I> {
  fun inputSerialName(): String

  fun inputToJson(value: I): String

  fun inputFromJson(value: String): I

  fun inputJsonSchema(): String
}

interface OutputSchema<O> {
  fun outputSerialName(): String

  fun outputToJson(value: O): String

  fun outputFromJson(value: String): O

  fun outputJsonSchema(): String

  fun elementsSchema(): OutputSchema<Elements<O>>
}

interface Schema<I, O> : InputSchema<I>, OutputSchema<O>

@Serializable
data class Input<A>(val value: A)

data class KotlinSchema<I, O>(
  val inputSerializer: KSerializer<I>,
  val outputSerializer: KSerializer<O>,
) : Schema<I, O> {

  override fun inputSerialName(): String =
    inputSerializer.descriptor.serialName

  override fun outputSerialName(): String =
    outputSerializer.descriptor.serialName

  override fun inputToJson(value: I): String =
    json.encodeToString(inputSerializer, value)

  override fun outputFromJson(value: String): O =
    json.decodeFromString(outputSerializer, value)

  override fun outputToJson(value: O): String =
    json.encodeToString(outputSerializer, value)

  override fun inputFromJson(value: String): I =
    json.decodeFromString(inputSerializer, value)

  override fun inputJsonSchema(): String =
    jsonSchema(inputSerializer).toString()

  override fun outputJsonSchema(): String =
    jsonSchema(outputSerializer).toString()

  override fun elementsSchema(): Schema<I, Elements<O>> =
    KotlinSchema(
      inputSerializer = inputSerializer,
      outputSerializer = Elements.Companion.serializer(outputSerializer)
    )

  private fun jsonSchema(serializer: KSerializer<*>): JsonObject {
    val finalSerializer = if (serializerRequiresWrapping(serializer)) {
      Input.serializer(serializer)
    } else {
      serializer
    }
    val jsonSchema = generateSchema(
      descriptor = finalSerializer.descriptor,
    )
    return json.parseToJsonElement(jsonSchema.toString()).jsonObject
  }

  private fun serializerRequiresWrapping(serializer: KSerializer<*>): Boolean {
    return when (serializer.descriptor.kind) {
      PolymorphicKind.OPEN,
      PolymorphicKind.SEALED -> false

      PrimitiveKind.BOOLEAN,
      PrimitiveKind.BYTE,
      PrimitiveKind.CHAR,
      PrimitiveKind.DOUBLE,
      PrimitiveKind.FLOAT,
      PrimitiveKind.INT,
      PrimitiveKind.LONG,
      PrimitiveKind.SHORT,
      StructureKind.LIST,
      PrimitiveKind.STRING -> true

      SerialKind.CONTEXTUAL,
      SerialKind.ENUM,
      StructureKind.CLASS,
      StructureKind.MAP,
      StructureKind.OBJECT -> false
    }
  }

  companion object {
    val json = Json {
      ignoreUnknownKeys = true
      isLenient = true
      prettyPrint = true
      encodeDefaults = true
      serializersModule = ArrowModule
      classDiscriminator = "type"
      classDiscriminatorMode = ClassDiscriminatorMode.ALL_JSON_OBJECTS
    }

    inline operator fun <reified I, reified O> invoke(): Schema<I, O> =
      KotlinSchema(
        inputSerializer = serializer<I>(),
        outputSerializer = serializer<O>(),
      )
  }
}
