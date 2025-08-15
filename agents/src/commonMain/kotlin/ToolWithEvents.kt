@file:OptIn(ExperimentalUuidApi::class)

package predictable

import kotlinx.coroutines.channels.ProducerScope
import predictable.tool.KotlinSchema
import predictable.tool.KotlinSchema.Companion.invoke
import predictable.tool.Schema
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

data class ToolWithEvents<in A, out B, E>(
  override val name: String,
  override val description: String,
  override val schema: Schema<*, *>,
  override val id: String,
  val scope: ProducerScope<E>,
  val block: suspend ProducerScope<E>.(input: A) -> B) : AI<A, B> {

  override suspend operator fun invoke(input: A): B =
    scope.block(input)

  companion object {
    inline operator fun <reified A, reified B, reified E> ProducerScope<E>.invoke(
      name: String = AI.name<A, B>(),
      description: String = AI.description<A, B>(),
      schema: Schema<A, B> = KotlinSchema<A, B>(),
      id: String = Uuid.random().toString(),
      noinline fn: suspend ProducerScope<E>.(A) -> B
    ): ToolWithEvents<A, B, E> = ToolWithEvents(name, description, schema, id, this,fn)

  }
}
