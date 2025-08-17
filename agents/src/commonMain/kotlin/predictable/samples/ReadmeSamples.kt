@file:Suppress("unused")
@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package predictable.samples

import kotlinx.coroutines.flow.collect
import kotlinx.serialization.Serializable
import predictable.Agent
import predictable.Tool
import predictable.ToolWithEvents
import predictable.agent.Model
import predictable.agent.Message

/**
 * README: Basic text generation example
 */
@DocumentationSample
suspend fun readmeBasicTextGeneration() {
    val agent = Agent(
        name = "assistant",
        description = "A helpful AI assistant",
        system = "You are a helpful assistant that provides clear and concise answers.",
        model = Model.default // Uses gpt-4o-mini by default
    )
    
    // Simple text generation using invoke operator
    val response = agent("What is Kotlin Multiplatform?")
    println(response)
}

/**
 * README: Type-safe structured generation
 */
@DocumentationSample
suspend fun readmeStructuredGeneration() {
    @Serializable
    data class Recipe(
        val name: String,
        val ingredients: List<String>,
        val instructions: List<String>,
        val prepTime: Int,
        val servings: Int
    )
    
    val agent = Agent(
        name = "chef",
        description = "Expert culinary AI",
        system = "You are a professional chef who creates detailed recipes.",
        model = Model.default
    )
    
    val recipe = agent.generateObject<String, Recipe>(
        "Create a recipe for chocolate chip cookies"
    )
    
    println("Recipe: ${recipe.name}")
    println("Prep time: ${recipe.prepTime} minutes")
    recipe.ingredients.forEach { println("- $it") }
}

/**
 * README: Streaming responses
 */
@DocumentationSample
suspend fun readmeStreamingResponses() {
    val agent = Agent(
        name = "storyteller",
        description = "Creative story writer",
        system = "You are a creative writer who tells engaging stories.",
        model = Model.default
    )
    
    agent.stream("Tell me a short story about a robot learning to paint")
        .collect { response ->
            when (response) {
                is predictable.agent.StreamResponse.Chunk -> print(response.value)
                else -> {} // Handle other response types if needed
            }
        }
}

/**
 * README: Tools example
 */
@DocumentationSample
suspend fun readmeToolsExample() {
    @Serializable
    data class WeatherRequest(val city: String)
    
    @Serializable
    data class WeatherResponse(
        val temperature: Double,
        val conditions: String,
        val humidity: Int
    )
    
    val weatherTool = Tool<WeatherRequest, WeatherResponse>(
        name = "get_weather",
        description = "Get current weather for a city"
    ) { request ->
        // Implement weather API call
        WeatherResponse(
            temperature = 22.5,
            conditions = "Partly cloudy",
            humidity = 65
        )
    }
    
    // Use with an agent
    val agent = Agent(
        name = "weather_assistant",
        description = "Weather information assistant",
        system = "You are a helpful weather assistant.",
        model = Model.default,
        tools = listOf(weatherTool)
    )
    
    val response = agent.chat("What's the weather in London?")
    println(response.value)
}

/**
 * README: Tools with events
 */
@DocumentationSample
@OptIn(kotlin.uuid.ExperimentalUuidApi::class)
suspend fun readmeToolsWithEvents() {
    @Serializable
    data class ProcessRequest(val files: List<String>)
    
    @Serializable
    data class ProcessResult(val processed: Int, val failed: Int)
    
    // ToolWithEvents must be created within a channelFlow scope
    kotlinx.coroutines.flow.channelFlow<String> {
        val processingTool = ToolWithEvents<ProcessRequest, ProcessResult, String>(
            name = "process_files",
            description = "Process multiple files with progress tracking",
            schema = predictable.tool.KotlinSchema<ProcessRequest, ProcessResult>(),
            id = kotlin.uuid.Uuid.random().toString(),
            scope = this
        ) { request ->
            var processed = 0
            
            request.files.forEach { file ->
                send("Processing $file...")
                // Process file
                processed++
                send("Completed $file (${processed}/${request.files.size})")
            }
            
            ProcessResult(processed = processed, failed = 0)
        }
        
        // Use the tool
        val result = processingTool(ProcessRequest(listOf("file1.txt", "file2.txt")))
        println("Processed: ${result.processed}")
    }.collect { event ->
        println(event) // Print progress events
    }
}

/**
 * README: Chat conversations
 */
@DocumentationSample
suspend fun readmeChatConversations() {
    val agent = Agent(
        name = "chatbot",
        description = "Conversational AI assistant",
        system = "You are a helpful and friendly assistant.",
        model = Model.default
    )
    
    // Start a conversation
    val firstResponse = agent.chat("Hello! I need help with Kotlin.")
    println(firstResponse.value)
    
    // Continue the conversation with context
    val messages = listOf(
        Message.user("Hello! I need help with Kotlin."),
        Message.assistant(firstResponse.value),
        Message.user("Can you show me an example?")
    )
    
    val followUp = agent.chat(messages)
    println(followUp.value)
}

/**
 * README: Streaming structured data
 */
@DocumentationSample
suspend fun readmeStreamingStructuredData() {
    @Serializable
    data class AnalysisResult(
        val summary: String? = null,
        val score: Double? = null,
        val recommendations: List<String>? = null
    )
    
    val agent = Agent(
        name = "analyzer",
        description = "Data analysis assistant",
        system = "You are a data analyst who provides detailed insights.",
        model = Model.default
    )
    
    agent.streamObject<String, AnalysisResult>("Analyze the market trends for AI startups")
        .collect { response ->
            when (response) {
                is predictable.agent.StreamResponse.Chunk -> {
                    // Handle partial results as they stream in
                    response.value?.summary?.let { println("Summary: $it") }
                    response.value?.score?.let { println("Score: $it") }
                }
                else -> {} // Handle other response types if needed
            }
        }
}

/**
 * README: Custom model configuration
 */
@DocumentationSample
suspend fun readmeCustomModelConfig() {
    // Use OpenRouter
    val openRouterModel = Model(
        apiUrl = "https://openrouter.ai/api/v1/",
        name = "anthropic/claude-3-opus"
    )
    
    // Use local model
    val localModel = Model(
        apiUrl = "http://localhost:8080/v1/",
        name = "llama-3.1-8b"
    )
    
    val agent = Agent(
        name = "custom_agent",
        description = "Custom model agent",
        system = "You are a helpful assistant.",
        model = openRouterModel,
        apiKey = dev.scottpierce.envvar.EnvVar["OPENROUTER_API_KEY"] ?: ""
    )
    
    val response = agent("Hello!")
    println(response)
}

/**
 * README: Tool composition
 */
@DocumentationSample
suspend fun readmeToolComposition() {
    // Combine multiple tools for complex workflows
    val searchTool = Tool<String, List<String>>(
        name = "search",
        description = "Search for information"
    ) { query ->
        // Search implementation
        listOf("result1", "result2")
    }
    
    val summaryTool = Tool<List<String>, String>(
        name = "summarize",
        description = "Summarize a list of results"
    ) { results ->
        // Summarization logic
        "Summary of ${results.size} results"
    }
    
    val agent = Agent(
        name = "researcher",
        description = "Research assistant",
        system = "You are a research assistant. Use search to find information, then summarize the results.",
        model = Model.default,
        tools = listOf(searchTool, summaryTool)
    )
    
    val response = agent("Find information about Kotlin coroutines")
    println(response)
}

/**
 * README: Q&A System example
 */
@DocumentationSample
class QASystemExample {
    @Serializable
    data class Question(
        val text: String,
        val context: String? = null
    )
    
    @Serializable
    data class Answer(
        val response: String,
        val confidence: Double,
        val sources: List<String> = emptyList()
    )
    
    private val agent = Agent(
        name = "qa_system",
        description = "Question answering system",
        system = """You are an expert Q&A system. 
                   |Provide accurate, well-sourced answers.
                   |Rate your confidence from 0.0 to 1.0.""".trimMargin(),
        model = Model.default
    )
    
    suspend fun answer(question: Question): Answer {
        return agent.generateObject<Question, Answer>(question)
    }
}

/**
 * README: Multi-agent collaboration
 */
@DocumentationSample
class MultiAgentSystemExample {
    private val researcher = Agent(
        name = "researcher",
        description = "Research specialist",
        system = "You are a research specialist who gathers information.",
        model = Model.default
    )
    
    private val analyst = Agent(
        name = "analyst",
        description = "Data analyst",
        system = "You analyze data and provide insights.",
        model = Model.default
    )
    
    private val writer = Agent(
        name = "writer",
        description = "Content writer",
        system = "You create clear, engaging content from analysis.",
        model = Model.default
    )
    
    suspend fun createReport(topic: String): String {
        // Research phase
        val research = researcher("Research: $topic")
        
        // Analysis phase
        val analysis = analyst("Analyze this research: $research")
        
        // Writing phase
        val report = writer("Write a report based on: $analysis")
        
        return report
    }
}