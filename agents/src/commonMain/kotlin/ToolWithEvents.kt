@file:OptIn(ExperimentalUuidApi::class)

package predictable

import kotlinx.coroutines.channels.ProducerScope
import predictable.tool.KotlinSchema
import predictable.tool.KotlinSchema.Companion.invoke
import predictable.tool.Schema
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * An enhanced [Tool] implementation that supports emitting events during execution.
 * 
 * This tool variant allows for real-time event emission during processing, useful for
 * progress tracking, intermediate results, logging, or any scenario requiring
 * observable execution stages.
 * 
 * @param A The input type this tool accepts (contravariant)
 * @param B The output type this tool produces (covariant)
 * @param E The type of events that can be emitted during execution
 * @property name The unique name identifier for this tool
 * @property description Human-readable description of the tool's functionality
 * @property schema Schema for input/output validation and serialization
 * @property id Unique identifier for this tool instance
 * @property scope The coroutine channel scope for emitting events
 * @property block The suspend function that implements the tool's logic with event emission
 * 
 * @sample predictable.samples.toolWithEventsProgressSample
 */
data class ToolWithEvents<in A, out B, E>(
  override val name: String,
  override val description: String,
  override val schema: Schema<*, *>,
  override val id: String,
  val scope: ProducerScope<E>,
  val block: suspend ProducerScope<E>.(input: A) -> B) : AI<A, B> {

  /**
   * Executes the tool's transformation function with event emission support.
   * 
   * @param input The input value to process
   * @return The transformed output from the tool's block function
   * @throws Exception if the block function throws or validation fails
   */
  override suspend operator fun invoke(input: A): B =
    scope.block(input)

  companion object {
    /**
     * Extension function to create a ToolWithEvents within a ProducerScope context.
     * 
     * Creates a tool that can emit events through the ProducerScope during execution.
     * This factory function must be called within a ProducerScope context.
     * 
     * @param A The input type (must be reified for schema generation)
     * @param B The output type (must be reified for schema generation)
     * @param E The event type that will be emitted
     * @param name The tool name (default: generated from types)
     * @param description The tool description (default: generated from types)
     * @param schema The schema for validation (default: KotlinSchema with type inference)
     * @param id Unique identifier (default: random UUID)
     * @param fn The suspend function implementing the tool's logic with event emission
     * @return A new ToolWithEvents instance configured for event emission
     * 
     * @sample predictable.samples.toolWithEventsExtensionSample
     */
    inline operator fun <reified A, reified B, reified E> ProducerScope<E>.invoke(
      name: String = AI.name<A, B>(),
      description: String = AI.description<A, B>(),
      schema: Schema<A, B> = KotlinSchema<A, B>(),
      id: String = Uuid.random().toString(),
      noinline fn: suspend ProducerScope<E>.(A) -> B
    ): ToolWithEvents<A, B, E> = ToolWithEvents(name, description, schema, id, this,fn)

  }
}
