@file:OptIn(ExperimentalUuidApi::class)

package predictable

import predictable.tool.Schema
import kotlin.reflect.typeOf
import kotlin.uuid.ExperimentalUuidApi

/**
 * Core abstraction for AI-powered operations that transform input of type [A] to output of type [B].
 * 
 * This interface represents any AI operation including tools, agents, and other AI-powered transformations.
 * Implementations must provide a schema for input/output validation and serialization.
 * 
 * @param A The input type this AI operation accepts (contravariant)
 * @param B The output type this AI operation produces (covariant)
 */
interface AI<in A, out B> {
  /**
   * The unique name identifier for this AI operation.
   * Should be descriptive and follow naming conventions (e.g., "fetchUserById", "generateSummary").
   */
  val name: String
  
  /**
   * Human-readable description of what this AI operation does.
   * Used for documentation and tool discovery by AI agents.
   */
  val description: String
  
  /**
   * Schema defining the structure of input and output types.
   * Used for validation, serialization, and generating API specifications.
   */
  val schema: Schema<*, *>
  
  /**
   * Unique identifier for this AI instance.
   * Typically a UUID string for tracking and debugging purposes.
   */
  val id: String

  /**
   * Executes the AI operation with the given input.
   * 
   * @param input The input value of type [A] to process
   * @return The transformed output of type [B]
   * @throws Exception if the operation fails or input validation fails
   */
  suspend operator fun invoke(input: A): B

  companion object {

    /**
     * Converts a suspend function into an AI operation using the unary plus operator.
     * Provides a concise syntax for creating [Tool] instances from lambda functions.
     * 
     * @param A The input type of the function
     * @param B The output type of the function
     * @return An [AI] instance wrapping the suspend function
     * @sample predictable.samples.aiUnaryPlusSample
     */
    inline operator fun <reified A, reified B> (suspend (A) -> B).unaryPlus(): AI<A, B> =
      Tool { input: A ->
        this@unaryPlus(input)
      }

    /**
     * Generates a default name for an AI operation based on input and output types.
     * Creates names in the format "fetchOutputByInput".
     * 
     * @param A The input type
     * @param B The output type
     * @return A generated name string based on the type names
     * @sample predictable.samples.aiNameGenerationSample
     */
    inline fun <reified A, reified B> name(): String {
      val input = typeNameToIdentifier<A>()
      val output = typeNameToIdentifier<B>()
      return "fetch${output}By${input}"
    }

    /**
     * Generates a default description for an AI operation based on input and output types.
     * Creates descriptions in the format "Input -> Output".
     * 
     * @param A The input type
     * @param B The output type
     * @return A generated description string showing the transformation
     * @sample predictable.samples.aiDescriptionGenerationSample
     */
    inline fun <reified A, reified B> description(): String {
      val input = typeNameToIdentifier<A>()
      val output = typeNameToIdentifier<B>()
      return "$input -> $output"
    }

    /**
     * Converts a type name to a valid identifier string.
     * Removes package names, special characters, and cleans up the result.
     * 
     * @param A The type to convert
     * @return A cleaned identifier string suitable for use in names
     * @sample predictable.samples.aiTypeNameToIdentifierSample
     */
    inline fun <reified A> typeNameToIdentifier() = typeOf<A>().toString()
      .substringAfterLast(".")
      .replace(Regex("[^a-zA-Z0-9]"), "_")
      .replace(Regex("_{2,}"), "_")
      .replace("_Kotlin_reflection_is_not_available", "")
      .trim { it == '_' }
      .trim()

  }
}

