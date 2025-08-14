@file:OptIn(ExperimentalUuidApi::class)

package predictable

import kotlinx.coroutines.channels.ProducerScope
import kotlinx.serialization.Serializable
import predictable.tool.KotlinSchema
import predictable.tool.Schema
import kotlin.reflect.typeOf
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

interface AI<in A, out B> {
  val name: String
  val description: String
  val schema: Schema<*, *>
  val id: String

  suspend operator fun invoke(input: A): B

  companion object {
    inline operator fun <reified A, reified B> invoke(
      name: String = name<A, B>(),
      description: String = description<A, B>(),
      schema: Schema<A, B> = KotlinSchema<A, B>(),
      id: String = Uuid.random().toString(),
      noinline fn: suspend (A) -> B
    ): AI<A, B> = Fn(name, description, schema, id, fn)

    inline fun <reified A, reified B, reified E> ProducerScope<E>.stream(
      name: String = name<A, B>(),
      description: String = description<A, B>(),
      schema: Schema<A, B> = KotlinSchema<A, B>(),
      id: String = Uuid.random().toString(),
      noinline fn: suspend ProducerScope<E>.(A) -> B
    ): FlowAI<A, B, E> = FlowAI(name, description, schema, id, this,fn)

    inline operator fun <reified A, reified B> (suspend (A) -> B).unaryPlus(): AI<A, B> =
      AI { input: A ->
        this@unaryPlus(input)
      }

    inline operator fun <reified A, reified B, reified C> (suspend (A, B) -> C).unaryPlus(): AI<Pair<A, B>, C> =
      AI { (a: A, b: B) ->
        this@unaryPlus(a, b)
      }

    inline fun <reified A, reified B> name(): String {
      val input = typeNameToIdentifier<A>()
      val output = typeNameToIdentifier<B>()
      return "fetch${output}By${input}"
    }

    inline fun <reified A, reified B> description(): String {
      val input = typeNameToIdentifier<A>()
      val output = typeNameToIdentifier<B>()
      return "$input -> $output"
    }

    inline fun <reified A> typeNameToIdentifier() = typeOf<A>().toString()
      .substringAfterLast(".")
      .replace(Regex("[^a-zA-Z0-9]"), "_")
      .replace(Regex("_{2,}"), "_")
      .replace("_Kotlin_reflection_is_not_available", "")
      .trim { it == '_' }
      .trim()

  }
}

data class FlowAI<in A, out B, E>(
  override val name: String,
  override val description: String,
  override val schema: Schema<*, *>,
  override val id: String,
  val scope: ProducerScope<E>,
  val block: suspend ProducerScope<E>.(input: A) -> B) : AI<A, B> {

  override suspend operator fun invoke(input: A): B =
    scope.block(input)
}

data class Fn<in A, out B>(
  override val name: String,
  override val description: String,
  override val schema: Schema<*, *>,
  override val id: String,
  val block: suspend (input: A) -> B) : AI<A, B> {

  override suspend operator fun invoke(input: A): B {
    return block(input)
  }
}
