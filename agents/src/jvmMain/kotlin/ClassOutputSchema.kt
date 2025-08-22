package predictable

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.jsonSchema.JsonSchema
import com.fasterxml.jackson.module.jsonSchema.JsonSchemaGenerator
import com.fasterxml.jackson.module.kotlin.KotlinModule
import predictable.tool.Elements
import predictable.tool.OutputSchema

/**
 * OutputSchema implementation using Java Class reflection.
 * Provides JSON serialization/deserialization and schema generation for output types.
 */
class ClassOutputSchema<O>(private val outputClass: Class<O>) : OutputSchema<O> {

  companion object {
    private val mapper = ObjectMapper().apply {
      registerModule(KotlinModule.Builder().build())
    }
    
    private val schemaGenerator = JsonSchemaGenerator(mapper)
  }

  override fun outputSerialName(): String = outputClass.simpleName

  override fun outputToJson(value: O): String = mapper.writeValueAsString(value)

  override fun outputFromJson(value: String): O = mapper.readValue(value, outputClass)

  override fun outputJsonSchema(): String {
    return try {
      val schema: JsonSchema = schemaGenerator.generateSchema(outputClass)
      mapper.writeValueAsString(schema)
    } catch (e: Exception) {
      // Fallback to basic schema if generation fails
      """
        {
          "type": "object",
          "title": "${outputClass.simpleName}",
          "description": "Schema for ${outputClass.name}"
        }
      """.trimIndent()
    }
  }

  override fun elementsSchema(): OutputSchema<Elements<O>> =
    ClassOutputSchema(Elements::class.java as Class<Elements<O>>)
}
