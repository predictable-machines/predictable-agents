package predictable

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.jsonSchema.JsonSchema
import com.fasterxml.jackson.module.jsonSchema.JsonSchemaGenerator
import com.fasterxml.jackson.module.kotlin.KotlinModule
import predictable.tool.InputSchema
import java.util.*

/**
 * InputSchema implementation using Java Class reflection.
 * Provides JSON serialization/deserialization and schema generation for input types.
 * 
 * Automatically wraps primitive types and collections in a JSON object structure
 * to comply with OpenAI's function calling requirements.
 */
class ClassInputSchema<I>(private val inputClass: Class<I>) : InputSchema<I> {

  companion object {
    private val mapper = ObjectMapper().apply {
      registerModule(KotlinModule.Builder().build())
    }
    
    private val schemaGenerator = JsonSchemaGenerator(mapper)
    
    // Primitive wrapper class mappings
    private val primitiveTypes = setOf(
      java.lang.String::class.java,
      java.lang.Boolean::class.java, java.lang.Boolean.TYPE,
      java.lang.Byte::class.java, java.lang.Byte.TYPE,
      java.lang.Short::class.java, java.lang.Short.TYPE,
      java.lang.Integer::class.java, java.lang.Integer.TYPE,
      java.lang.Long::class.java, java.lang.Long.TYPE,
      java.lang.Float::class.java, java.lang.Float.TYPE,
      java.lang.Double::class.java, java.lang.Double.TYPE,
      java.lang.Character::class.java, java.lang.Character.TYPE
    )
    
    private fun isPrimitive(clazz: Class<*>): Boolean {
      return clazz.isPrimitive || primitiveTypes.contains(clazz)
    }
    
    private fun isCollection(clazz: Class<*>): Boolean {
      return Collection::class.java.isAssignableFrom(clazz) || 
             clazz.isArray
    }
  }

  private val needsWrapping = isPrimitive(inputClass) || isCollection(inputClass)

  override fun inputSerialName(): String = 
    if (needsWrapping) "${inputClass.simpleName}Input" else inputClass.simpleName

  override fun inputToJson(value: I): String {
    return if (needsWrapping) {
      // Wrap primitive or collection in an object with a "value" field
      val node = mapper.createObjectNode()
      node.set<ObjectNode>("value", mapper.valueToTree(value))
      mapper.writeValueAsString(node)
    } else {
      mapper.writeValueAsString(value)
    }
  }

  override fun inputFromJson(value: String): I {
    return if (needsWrapping) {
      // Unwrap the value from the object wrapper
      val node = mapper.readTree(value)
      val valueNode = node.get("value") ?: throw IllegalArgumentException("Missing 'value' field in JSON")
      mapper.treeToValue(valueNode, inputClass)
    } else {
      mapper.readValue(value, inputClass)
    }
  }

  override fun inputJsonSchema(): String {
    return if (needsWrapping) {
      // Generate a wrapper schema for primitives and collections
      val typeString = when {
        inputClass == java.lang.String::class.java -> "string"
        inputClass == java.lang.Boolean::class.java || inputClass == java.lang.Boolean.TYPE -> "boolean"
        inputClass == java.lang.Integer::class.java || inputClass == java.lang.Integer.TYPE -> "integer"
        inputClass == java.lang.Long::class.java || inputClass == java.lang.Long.TYPE -> "integer"
        inputClass == java.lang.Float::class.java || inputClass == java.lang.Float.TYPE -> "number"
        inputClass == java.lang.Double::class.java || inputClass == java.lang.Double.TYPE -> "number"
        isCollection(inputClass) -> "array"
        else -> "string"
      }
      
      """
        {
          "type": "object",
          "title": "${inputSerialName()}",
          "description": "Wrapper for ${inputClass.simpleName}",
          "properties": {
            "value": {
              "type": "$typeString",
              "description": "The ${inputClass.simpleName} value"
            }
          },
          "required": ["value"]
        }
      """.trimIndent()
    } else {
      try {
        val schema: JsonSchema = schemaGenerator.generateSchema(inputClass)
        mapper.writeValueAsString(schema)
      } catch (e: Exception) {
        // Fallback to basic schema if generation fails
        """
          {
            "type": "object",
            "title": "${inputClass.simpleName}",
            "description": "Schema for ${inputClass.name}"
          }
        """.trimIndent()
      }
    }
  }
}