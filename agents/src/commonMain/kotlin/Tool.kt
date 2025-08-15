@file:OptIn(ExperimentalUuidApi::class)

package predictable

import predictable.tool.KotlinSchema
import predictable.tool.Schema
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

data class Tool<in A, out B>(
  override val name: String,
  override val description: String,
  override val schema: Schema<*, *>,
  override val id: String,
  val block: suspend (input: A) -> B) : AI<A, B> {

  override suspend operator fun invoke(input: A): B {
    return block(input)
  }

  companion object {
    inline operator fun <reified A, reified B> invoke(
      name: String = AI.name<A, B>(),
      description: String = AI.description<A, B>(),
      schema: Schema<A, B> = KotlinSchema<A, B>(),
      id: String = Uuid.random().toString(),
      noinline fn: suspend (A) -> B
    ): Tool<A, B> = Tool(name, description, schema, id, fn)
  }
}
