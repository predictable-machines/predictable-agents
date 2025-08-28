@file:OptIn(ExperimentalUuidApi::class)

package predictable

import predictable.tool.KotlinSchema
import predictable.tool.Schema
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Factory functions for creating Tool instances.
 */
object ToolFactory {
  
  /**
   * Creates a Tool with type inference and default values using a suspend function.
   * 
   * @param A The input type (must be reified for schema generation)
   * @param B The output type (must be reified for schema generation)
   * @param name The tool name (default: generated from types)
   * @param description The tool description (default: generated from types)
   * @param schema The schema for validation (default: KotlinSchema with type inference)
   * @param id Unique identifier (default: random UUID)
   * @param fn The suspend function implementing the tool's logic
   * @return A new Tool instance with the specified configuration
   */
  inline fun <reified A, reified B> create(
    name: String = AI.name<A, B>(),
    description: String = AI.description<A, B>(),
    schema: Schema<A, B> = KotlinSchema<A, B>(),
    id: String = Uuid.random().toString(),
    noinline fn: suspend (A) -> B
  ): Tool<A, B> = createTool(name, description, schema, id, fn)
}

/**
 * Kotlin DSL for creating a Tool with a suspend function.
 * Allows the convenient syntax: Tool { ... }
 * 
 * @param block The suspend function implementing the tool's logic
 * @return A new Tool instance
 */
inline fun <reified A, reified B> Tool(
  noinline block: suspend (A) -> B
): Tool<A, B> = createTool(
  name = AI.name<A, B>(),
  description = AI.description<A, B>(),
  schema = KotlinSchema<A, B>(),
  id = Uuid.random().toString(),
  fn = block
)

/**
 * Kotlin DSL for creating a Tool with just a name and a suspend function.
 * Allows the convenient syntax: Tool(name) { ... }
 * 
 * @param name The tool name
 * @param block The suspend function implementing the tool's logic
 * @return A new Tool instance
 */
inline fun <reified A, reified B> Tool(
  name: String,
  noinline block: suspend (A) -> B
): Tool<A, B> = createTool(
  name = name,
  description = AI.description<A, B>(),
  schema = KotlinSchema<A, B>(),
  id = Uuid.random().toString(),
  fn = block
)

/**
 * Kotlin DSL for creating a Tool with name, description and a suspend function.
 * Allows the convenient syntax: Tool(name, description) { ... }
 * 
 * @param name The tool name
 * @param description The tool description
 * @param block The suspend function implementing the tool's logic
 * @return A new Tool instance
 */
inline fun <reified A, reified B> Tool(
  name: String,
  description: String,
  noinline block: suspend (A) -> B
): Tool<A, B> = createTool(
  name = name,
  description = description,
  schema = KotlinSchema<A, B>(),
  id = Uuid.random().toString(),
  fn = block
)

/**
 * Kotlin DSL for creating a Tool with name, description, schema and a suspend function.
 * Allows the convenient syntax: Tool(name, description, schema) { ... }
 * 
 * @param name The tool name
 * @param description The tool description
 * @param schema The schema for validation
 * @param block The suspend function implementing the tool's logic
 * @return A new Tool instance
 */
inline fun <reified A, reified B> Tool(
  name: String,
  description: String,
  schema: Schema<A, B>,
  noinline block: suspend (A) -> B
): Tool<A, B> = createTool(
  name = name,
  description = description,
  schema = schema,
  id = Uuid.random().toString(),
  fn = block
)

/**
 * Platform-specific tool creation function.
 */
expect fun <A, B> createTool(
  name: String,
  description: String,
  schema: Schema<*, *>,
  id: String,
  fn: suspend (A) -> B
): Tool<A, B>