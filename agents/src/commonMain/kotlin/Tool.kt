@file:OptIn(ExperimentalUuidApi::class)

package predictable

import predictable.tool.KotlinSchema
import predictable.tool.Schema
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * A concrete implementation of the [AI] interface that wraps a suspend function as an AI-powered tool.
 * 
 * Tools are the fundamental building blocks for creating AI operations. They encapsulate
 * a transformation function along with metadata needed for validation and discovery.
 * 
 * @param A The input type this tool accepts (contravariant)
 * @param B The output type this tool produces (covariant)
 * @property name The unique name identifier for this tool
 * @property description Human-readable description of the tool's functionality
 * @property schema Schema for input/output validation and serialization
 * @property id Unique identifier for this tool instance
 * @property block The suspend function that implements the tool's logic
 * 
 * Example:
 * ```kotlin
 * val summarizer = Tool<String, String>(
 *   name = "summarize",
 *   description = "Summarizes long text into key points",
 *   schema = KotlinSchema<String, String>(),
 *   id = Uuid.random().toString()
 * ) { input ->
 *   // AI processing logic here
 *   "Summary of: $input"
 * }
 * ```
 */
data class Tool<in A, out B>(
  override val name: String,
  override val description: String,
  override val schema: Schema<*, *>,
  override val id: String,
  val block: suspend (input: A) -> B) : AI<A, B> {

  /**
   * Executes the tool's transformation function with the provided input.
   * 
   * @param input The input value to process
   * @return The transformed output from the tool's block function
   * @throws Exception if the block function throws or validation fails
   */
  override suspend operator fun invoke(input: A): B {
    return block(input)
  }

  companion object {
    /**
     * Factory function to create a Tool with type inference and default values.
     * 
     * Provides a convenient way to create tools with automatic schema generation
     * and sensible defaults for name and description based on type information.
     * 
     * @param A The input type (must be reified for schema generation)
     * @param B The output type (must be reified for schema generation)
     * @param name The tool name (default: generated from types)
     * @param description The tool description (default: generated from types)
     * @param schema The schema for validation (default: KotlinSchema with type inference)
     * @param id Unique identifier (default: random UUID)
     * @param fn The suspend function implementing the tool's logic
     * @return A new Tool instance with the specified configuration
     * 
     * Example:
     * ```kotlin
     * val calculator = Tool<CalculatorInput, CalculatorOutput> { input ->
     *   CalculatorOutput(result = input.a + input.b)
     * }
     * ```
     */
    inline operator fun <reified A, reified B> invoke(
      name: String = AI.name<A, B>(),
      description: String = AI.description<A, B>(),
      schema: Schema<A, B> = KotlinSchema<A, B>(),
      id: String = Uuid.random().toString(),
      noinline fn: suspend (A) -> B
    ): Tool<A, B> = Tool(name, description, schema, id, fn)
  }
}
