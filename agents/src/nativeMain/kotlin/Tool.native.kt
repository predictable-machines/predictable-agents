@file:OptIn(ExperimentalUuidApi::class)

package predictable

import predictable.tool.Schema
import kotlin.uuid.ExperimentalUuidApi

/**
 * Native implementation of Tool.
 */
actual data class Tool<in A, out B>(
  actual override val name: String,
  actual override val description: String,
  actual override val schema: Schema<*, *>,
  actual override val id: String,
  val block: suspend (input: A) -> B
) : AI<A, B> {

  actual override suspend operator fun invoke(input: A): B {
    return block(input)
  }
}

/**
 * Platform-specific tool creation function implementation for Native.
 */
actual fun <A, B> createTool(
  name: String,
  description: String,
  schema: Schema<*, *>,
  id: String,
  fn: suspend (A) -> B
): Tool<A, B> = Tool(name, description, schema, id, fn)