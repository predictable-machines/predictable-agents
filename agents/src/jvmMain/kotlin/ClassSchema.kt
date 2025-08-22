package predictable

import predictable.tool.Elements
import predictable.tool.OutputSchema
import predictable.tool.Schema

/**
 * Combined Schema implementation using Java Class reflection for both input and output.
 * Composes ClassInputSchema and ClassOutputSchema for complete bidirectional type conversion.
 */
class ClassSchema<I, O>(
  inputClass: Class<I>,
  outputClass: Class<O>
) : Schema<I, O> {

  private val inputSchema = ClassInputSchema(inputClass)
  private val outputSchema = ClassOutputSchema(outputClass)

  // Delegate input methods to inputSchema
  override fun inputSerialName(): String = inputSchema.inputSerialName()

  override fun inputToJson(value: I): String = inputSchema.inputToJson(value)

  override fun inputFromJson(value: String): I = inputSchema.inputFromJson(value)

  override fun inputJsonSchema(): String = inputSchema.inputJsonSchema()

  // Delegate output methods to outputSchema
  override fun outputSerialName(): String = outputSchema.outputSerialName()

  override fun outputToJson(value: O): String = outputSchema.outputToJson(value)

  override fun outputFromJson(value: String): O = outputSchema.outputFromJson(value)

  override fun outputJsonSchema(): String = outputSchema.outputJsonSchema()

  override fun elementsSchema(): OutputSchema<Elements<O>> = outputSchema.elementsSchema()
}
