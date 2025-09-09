@file:OptIn(ExperimentalUuidApi::class)

package predictable

import predictable.tool.Schema
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.future.future
import java.util.concurrent.CompletableFuture
import java.util.function.Function
import java.lang.invoke.SerializedLambda
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType

/**
 * JVM implementation of Tool that supports both suspend and regular functions.
 */
actual data class Tool<in A, out B> @JvmOverloads constructor(
  actual override val name: String,
  actual override val description: String,
  actual override val schema: Schema<*, *>,
  actual override val id: String = Uuid.random().toString(),
  val block: suspend (input: A) -> B
) : AI<A, B> {
  
  /**
   * Coroutine scope with SupervisorJob for safe async execution.
   * Uses SupervisorJob to isolate failures and prevent cancellation propagation.
   */
  private val coroutineScope = CoroutineScope(SupervisorJob())

  /**
   * Secondary constructor for Java interop with regular functions.
   * Wraps a non-suspend function as a suspend function.
   */
  @JvmOverloads
  constructor(
    name: String,
    description: String,
    schema: Schema<*, *>,
    function: Function<A, B>,
    id: String = Uuid.random().toString()
  ) : this(name, description, schema, id, { input: A ->
    function.apply(input)
  })

  /**
   * Executes the tool's transformation function with the provided input.
   * 
   * @param input The input value to process
   * @return The transformed output from the tool's block function
   * @throws Exception if the block function throws or validation fails
   */
  actual override suspend operator fun invoke(input: A): B {
    return block(input)
  }
  
  /**
   * Async invoke for Java interop, returns a CompletableFuture.
   * 
   * @param input The input value to process
   * @return A CompletableFuture containing the transformed output
   */
  fun invokeAsync(input: A): CompletableFuture<out B> = 
    coroutineScope.future {
      invoke(input)
    }
  
  /**
   * Blocking invoke for Java interop.
   * Blocks the current thread until the result is available.
   * 
   * @param input The input value to process
   * @return The transformed output
   */
  fun invokeBlocking(input: A): B = 
    invokeAsync(input).get()

  companion object {
    
    /**
     * Creates a Tool from a Java Function using array type hack to infer types.
     * This eliminates the need to pass explicit Schema or Class parameters.
     * 
     * The array type hack works by using varargs which creates an array at runtime,
     * and arrays retain their component type information even after type erasure.
     * 
     * Usage: Tool.create("name", "description", function)
     * Or with explicit types: Tool.<Input, Output>create("name", "description", function)
     * 
     * @param name The tool name
     * @param description The tool description  
     * @param function A Java Function for the transformation
     * @param reified Varargs parameter for type inference using array hack
     * @return A new Tool instance
     */
    @JvmStatic
    fun <A, B> of(
      name: String,
      description: String,
      function: Function<A, B>,
      vararg reified: A?
    ): Tool<A, B> {
      // Extract input type from the reified array
      @Suppress("UNCHECKED_CAST")
      val inputClass = reified.javaClass.componentType as Class<A>

      val outputClass = inferOutputClass(function)

      val schema = ClassSchema(inputClass, outputClass)
      val id = Uuid.random().toString()
      return Tool(name, description, schema, function, id)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <A, B> inferOutputClass(function: Function<A, B>): Class<B> {
      val functionClass = function.javaClass

      // 1. Try to read generic parameters from the implemented Function interface
      val genericOutput = functionClass.genericInterfaces
        .asSequence()
        .filterIsInstance<ParameterizedType>()
        .firstOrNull { it.rawType == Function::class.java }
        ?.actualTypeArguments?.getOrNull(1)
        ?.let { toClass<B>(it) }

      if (genericOutput != null && genericOutput != Any::class.java) {
        return genericOutput
      }

      // 2. Fallback to SerializedLambda for lambdas implementing Serializable
      val serializedLambda = try {
        val writeReplace = functionClass.getDeclaredMethod("writeReplace").apply { isAccessible = true }
        writeReplace.invoke(function) as? SerializedLambda
      } catch (_: Throwable) {
        null
      }

      if (serializedLambda != null) {
        val returnDesc = serializedLambda.implMethodSignature.substringAfter(')').substringBeforeLast(';')
        val className = returnDesc.removePrefix("L").replace('/', '.')
        return Class.forName(className) as Class<B>
      }

      // 3. Fallback to raw apply return type (will be Object for most lambdas)
      val applyMethod = functionClass.methods.find { it.name == "apply" }
        ?: throw IllegalArgumentException("Function does not have apply method")

      return applyMethod.returnType as Class<B>
    }

    @Suppress("UNCHECKED_CAST")
    private fun <B> toClass(type: Type): Class<B>? = when (type) {
      is Class<*> -> type as Class<B>
      is ParameterizedType -> toClass<B>(type.rawType)
      is WildcardType -> type.upperBounds.firstOrNull()?.let { toClass<B>(it) }
      else -> null
    }
    
    /**
     * Creates a Tool from a regular Kotlin function (non-suspend).
     * 
     * @param name The tool name
     * @param description The tool description
     * @param schema The schema for validation
     * @param fn A non-suspend function for the transformation
     * @param id Unique identifier (default: random UUID)
     * @return A new Tool instance
     */
    @JvmStatic
    @JvmOverloads
    fun <A, B> fromKotlinFunction(
      name: String,
      description: String,
      schema: Schema<A, B>,
      fn: (A) -> B,
      id: String = Uuid.random().toString()
    ): Tool<A, B> = Tool(name, description, schema, id) { input: A ->
      fn(input)
    }
    
    /**
     * Creates a Tool from a suspend function.
     * Primarily for Kotlin users who need coroutine support.
     * 
     * @param name The tool name
     * @param description The tool description
     * @param schema The schema for validation
     * @param fn A suspend function for the transformation
     * @param id Unique identifier (default: random UUID)
     * @return A new Tool instance
     */
    @JvmStatic
    @JvmOverloads
    fun <A, B> fromSuspendFunction(
      name: String,
      description: String,
      schema: Schema<A, B>,
      fn: suspend (A) -> B,
      id: String = Uuid.random().toString()
    ): Tool<A, B> = Tool(name, description, schema, id, fn)
  }
}

/**
 * Platform-specific tool creation function implementation for JVM.
 */
actual fun <A, B> createTool(
  name: String,
  description: String,
  schema: Schema<*, *>,
  id: String,
  fn: suspend (A) -> B
): Tool<A, B> = Tool(name, description, schema, id, fn)
