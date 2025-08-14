@file:OptIn(ExperimentalUuidApi::class)

package predictable.mcp.tools

import io.modelcontextprotocol.kotlin.sdk.Tool
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import predictable.AI
import predictable.tool.Elements
import predictable.tool.KotlinSchema
import predictable.tool.OutputSchema
import predictable.tool.Schema
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

data class MCPTool(
  val tool: Tool,
  override val id: String = Uuid.Companion.random().toString(),
  override val name: String = tool.name,
  override val description: String = tool.description ?: "",
  val block: suspend (input: JsonObject) -> JsonObject
) : AI<JsonObject, JsonObject> {

  override val schema: Schema<*, *>
    get() = object : Schema<JsonObject, JsonObject> {
      override fun inputSerialName(): String = tool.inputSchema.type

      override fun inputToJson(value: JsonObject): String = value.toString()

      override fun inputFromJson(value: String): JsonObject = KotlinSchema.Companion.json.parseToJsonElement(value).jsonObject

      override fun inputJsonSchema(): String {
        val fixedSchema = tool.inputSchema.copy(
          required = tool.inputSchema.required ?: emptyList(),
        )
        val encodedSchema = KotlinSchema.Companion.json.encodeToString(fixedSchema)
        return encodedSchema
      }

      override fun outputSerialName(): String = tool.name + "_Output"

      override fun outputToJson(value: JsonObject): String = value.toString()

      override fun outputFromJson(value: String): JsonObject = KotlinSchema.Companion.json.parseToJsonElement(value).jsonObject

      override fun outputJsonSchema(): String = throw UnsupportedOperationException("Not yet implemented")

      override fun elementsSchema(): OutputSchema<Elements<JsonObject>> = throw UnsupportedOperationException("Not yet implemented")
    }


  override suspend operator fun invoke(input: JsonObject): JsonObject {
    return block(input)
  }
}
