@file:OptIn(ExperimentalUuidApi::class)

package predictable

import predictable.tool.Schema
import kotlin.reflect.typeOf
import kotlin.uuid.ExperimentalUuidApi

interface AI<in A, out B> {
  val name: String
  val description: String
  val schema: Schema<*, *>
  val id: String

  suspend operator fun invoke(input: A): B

  companion object {

    inline operator fun <reified A, reified B> (suspend (A) -> B).unaryPlus(): AI<A, B> =
      Tool { input: A ->
        this@unaryPlus(input)
      }

    inline operator fun <reified A, reified B, reified C> (suspend (A, B) -> C).unaryPlus(): AI<Pair<A, B>, C> =
      Tool { (a: A, b: B) ->
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

