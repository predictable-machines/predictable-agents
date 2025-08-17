@file:OptIn(ExperimentalUuidApi::class)
@file:Suppress("unused")

package predictable.samples

import predictable.Tool
import predictable.tool.KotlinSchema
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Sample demonstrating how to create a Tool with explicit parameters
 */
@DocumentationSample
suspend fun toolCreationSample() {
    val summarizer = Tool<String, String>(
        name = "summarize",
        description = "Summarizes long text into key points",
        schema = KotlinSchema<String, String>(),
        id = Uuid.random().toString(),
        block = { input ->
            // AI processing logic here
            "Summary of: $input"
        }
    )
    
    // Usage
    val summary = summarizer("This is a long text that needs to be summarized...")
    println(summary) // "Summary of: This is a long text that needs to be summarized..."
}

/**
 * Sample demonstrating how to create a Tool with structured input/output
 */
@DocumentationSample
suspend fun toolStructuredSample() {
    data class CalculatorInput(val a: Double, val b: Double, val operation: String)
    data class CalculatorOutput(val result: Double, val formula: String)
    
    val calculator = Tool<CalculatorInput, CalculatorOutput>(
        name = "calculator",
        description = "Performs mathematical operations"
    ) { input ->
        val result = when (input.operation) {
            "add" -> input.a + input.b
            "subtract" -> input.a - input.b
            "multiply" -> input.a * input.b
            "divide" -> if (input.b != 0.0) input.a / input.b else 0.0
            else -> 0.0
        }
        CalculatorOutput(
            result = result,
            formula = "${input.a} ${input.operation} ${input.b} = $result"
        )
    }
    
    // Usage
    val calculation = calculator(CalculatorInput(10.0, 5.0, "add"))
    println(calculation.formula) // "10.0 add 5.0 = 15.0"
}

/**
 * Sample demonstrating Tool factory function with type inference
 */
@DocumentationSample
suspend fun toolFactorySample() {
    data class TranslationRequest(val text: String, val targetLanguage: String)
    data class TranslationResponse(val translatedText: String, val sourceLanguage: String)
    
    // Using the companion object's invoke operator for cleaner syntax
    val translator = Tool<TranslationRequest, TranslationResponse> { request ->
        // Simulated translation logic
        TranslationResponse(
            translatedText = when (request.targetLanguage) {
                "es" -> "Hola mundo" // if request.text was "Hello world"
                "fr" -> "Bonjour le monde"
                else -> request.text
            },
            sourceLanguage = "en"
        )
    }
    
    val translation = translator(TranslationRequest("Hello world", "es"))
    println(translation.translatedText) // "Hola mundo"
}