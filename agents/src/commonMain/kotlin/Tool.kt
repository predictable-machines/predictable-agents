@file:OptIn(ExperimentalUuidApi::class)

package predictable

import predictable.tool.KotlinSchema
import predictable.tool.Schema
import kotlin.jvm.JvmOverloads
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * A concrete implementation of the [AI] interface that wraps a function as an AI-powered tool.
 * 
 * Tools are the fundamental building blocks for creating AI operations. They encapsulate
 * a transformation function along with metadata needed for validation and discovery.
 * 
 * @param A The input type this tool accepts (contravariant)
 * @param B The output type this tool produces (covariant)
 * 
 * @sample predictable.samples.toolCreationSample
 * @sample predictable.samples.toolStructuredSample
 */
expect class Tool<in A, out B> : AI<A, B> {
  override val name: String
  override val description: String
  override val schema: Schema<*, *>
  override val id: String
  
  /**
   * Executes the tool's transformation function with the provided input.
   * 
   * @param input The input value to process
   * @return The transformed output from the tool's function
   * @throws Exception if the function throws or validation fails
   */
  override suspend operator fun invoke(input: A): B
}
