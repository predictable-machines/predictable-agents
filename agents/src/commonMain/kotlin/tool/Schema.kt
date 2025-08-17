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

/**
 * Schema interface for handling input validation and serialization.
 * 
 * Provides methods for converting input values to/from JSON and generating
 * JSON schemas for validation and API documentation.
 * 
 * @param I The input type this schema handles
 */
interface InputSchema<I> {
  /**
   * Returns the serial name for the input type.
   * Used for type identification in serialized formats.
   */
  fun inputSerialName(): String

  /**
   * Serializes an input value to JSON string.
   * 
   * @param value The input value to serialize
   * @return JSON string representation of the input
   */
  fun inputToJson(value: I): String

  /**
   * Deserializes an input value from JSON string.
   * 
   * @param value The JSON string to deserialize
   * @return The deserialized input value of type [I]
   */
  fun inputFromJson(value: String): I

  /**
   * Generates a JSON Schema for the input type.
   * Used for validation and API documentation.
   * 
   * @return JSON Schema as a string
   */
  fun inputJsonSchema(): String
}

/**
 * Schema interface for handling output validation and serialization.
 * 
 * Provides methods for converting output values to/from JSON and generating
 * JSON schemas for validation and API documentation.
 * 
 * @param O The output type this schema handles
 */
interface OutputSchema<O> {
  /**
   * Returns the serial name for the output type.
   * Used for type identification in serialized formats.
   */
  fun outputSerialName(): String

  /**
   * Serializes an output value to JSON string.
   * 
   * @param value The output value to serialize
   * @return JSON string representation of the output
   */
  fun outputToJson(value: O): String

  /**
   * Deserializes an output value from JSON string.
   * 
   * @param value The JSON string to deserialize
   * @return The deserialized output value of type [O]
   */
  fun outputFromJson(value: String): O

  /**
   * Generates a JSON Schema for the output type.
   * Used for validation and API documentation.
   * 
   * @return JSON Schema as a string
   */
  fun outputJsonSchema(): String

  /**
   * Creates a schema for streaming elements of the output type.
   * Used for handling partial results in streaming responses.
   * 
   * @return Schema for Elements wrapper containing output type
   */
  fun elementsSchema(): OutputSchema<Elements<O>>
}

/**
 * Combined schema interface for both input and output handling.
 * 
 * Provides complete schema functionality for bidirectional type conversion
 * and validation, supporting both request and response processing.
 * 
 * @param I The input type this schema handles
 * @param O The output type this schema handles
 */
interface Schema<I, O> : InputSchema<I>, OutputSchema<O>

/**
 * Wrapper class for input values requiring serialization.
 * 
 * Used internally to wrap primitive types and simple values
 * that need proper JSON object structure for schema generation.
 * 
 * @param A The type of the wrapped value
 * @property value The wrapped input value
 */
@Serializable
data class Input<A>(val value: A)

/**
 * Kotlin-specific implementation of Schema using KSerializer.
 * 
 * Provides automatic schema generation and JSON serialization/deserialization
 * using Kotlin serialization framework. Handles complex types including
 * Arrow types (Either, Option, etc.) through custom serializers.
 * 
 * @param I The input type
 * @param O The output type
 * @property inputSerializer Serializer for the input type
 * @property outputSerializer Serializer for the output type
 */
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
