@file:Suppress("unused")

package predictable.samples

import kotlinx.coroutines.flow.collect
import predictable.Agent
import predictable.agent.Model
import predictable.agent.Message
import predictable.AI
import predictable.Tool

/**
 * Sample demonstrating basic agent creation and text generation
 */
@DocumentationSample
suspend fun agentBasicUsageSample() {
    val assistant = Agent(
        name = "CodeAssistant",
        description = "Helps with coding tasks",
        system = "You are a helpful coding assistant that provides clear, concise answers.",
        model = Model(Model.openAIBaseUrl, "gpt-4"),
        apiKey = "your-api-key" // In practice, use environment variables
    )
    
    // Simple text generation
    val response = assistant("Explain the difference between val and var in Kotlin")
    println(response)
}

/**
 * Sample demonstrating agent as a tool converter
 */
@DocumentationSample
suspend fun agentAsToolSample() {
    data class TranslateRequest(val text: String, val targetLanguage: String)
    data class TranslateResponse(val translatedText: String, val confidence: Double)
    
    val translatorAgent = Agent(
        name = "translator",
        description = "Translates text between languages",
        system = "You are a professional translator. Translate the given text accurately.",
        model = Model(Model.openAIBaseUrl, "gpt-3.5-turbo")
    )
    
    // Convert agent to a strongly-typed tool
    val translatorTool: Tool<TranslateRequest, TranslateResponse> = 
        translatorAgent<TranslateRequest, TranslateResponse>()
    
    val result = translatorTool(TranslateRequest("Hello, world!", "Spanish"))
    println("Translation: ${result.translatedText} (confidence: ${result.confidence})")
}

/**
 * Sample demonstrating structured object generation
 */
@DocumentationSample
suspend fun agentGenerateObjectSample() {
    data class CodeRequest(
        val language: String,
        val task: String,
        val requirements: List<String>
    )
    
    data class CodeResponse(
        val code: String,
        val explanation: String,
        val complexity: String
    )
    
    val codeGenerator = Agent(
        name = "codeGenerator",
        description = "Generates code based on requirements",
        system = "You are an expert programmer. Generate clean, efficient code.",
        model = Model(Model.openAIBaseUrl, "gpt-4")
    )
    
    val generatedCode = codeGenerator.generateObject<CodeRequest, CodeResponse>(
        CodeRequest(
            language = "Kotlin",
            task = "Sort a list of integers",
            requirements = listOf("Use functional programming", "Handle edge cases")
        )
    )
    
    println("Generated code:\n${generatedCode.code}")
    println("Explanation: ${generatedCode.explanation}")
}

/**
 * Sample demonstrating streaming responses
 */
@DocumentationSample
suspend fun agentStreamingSample() {
    val storyteller = Agent(
        name = "storyteller",
        description = "Creates engaging stories",
        system = "You are a creative storyteller who writes engaging narratives.",
        model = Model(Model.openAIBaseUrl, "gpt-4")
    )
    
    // Stream text responses
    storyteller.stream("Write a short story about a robot learning to paint").collect { chunk ->
        // Print each chunk as it arrives for real-time output
        print(chunk)
    }
}

/**
 * Sample demonstrating structured streaming
 */
@DocumentationSample
suspend fun agentStructuredStreamingSample() {
    data class AnalysisRequest(val topic: String, val depth: String)
    data class AnalysisResult(
        val summary: String,
        val keyPoints: List<String>,
        val recommendations: List<String>
    )
    
    val analyst = Agent(
        name = "analyst",
        description = "Performs in-depth analysis",
        system = "You are a thorough analyst who provides detailed insights.",
        model = Model(Model.openAIBaseUrl, "gpt-4")
    )
    
    // Stream structured objects
    analyst.stream<AnalysisRequest, AnalysisResult>(
        AnalysisRequest("AI in Healthcare", "comprehensive")
    ).collect { partialResult ->
        // Handle partial updates to the analysis
        println("Partial result received: $partialResult")
    }
}

/**
 * Sample demonstrating chat with conversation history
 */
@DocumentationSample
suspend fun agentChatWithHistorySample() {
    val chatbot = Agent(
        name = "chatbot",
        description = "Engaging conversational AI",
        system = "You are a helpful and friendly assistant.",
        model = Model(Model.openAIBaseUrl, "gpt-3.5-turbo")
    )
    
    // Build conversation history
    val conversation = listOf(
        Message.user("What's the weather like?"),
        Message.assistant("I don't have access to real-time weather data, but I can help you understand weather patterns."),
        Message.user("What causes rain?")
    )
    
    // Get response considering full conversation context
    val response = chatbot.chat(conversation)
    println("Response: ${response.value}")
    println("Tokens used: ${response.metadata?.totalTokens}")
}

/**
 * Sample demonstrating agent with tools
 */
@DocumentationSample
suspend fun agentWithToolsSample() {
    // Define some tools the agent can use
    val searchTool = Tool<String, String>(
        name = "search",
        description = "Search for information"
    ) { query ->
        "Search results for: $query"
    }
    
    val calculatorTool = Tool<String, Double>(
        name = "calculate",
        description = "Perform calculations"
    ) { expression ->
        // Simple calculation logic
        42.0 // Placeholder
    }
    
    val assistant = Agent(
        name = "assistantWithTools",
        description = "Assistant with access to tools",
        system = "You are an assistant that can search and calculate.",
        model = Model(Model.openAIBaseUrl, "gpt-4"),
        tools = listOf(searchTool, calculatorTool)
    )
    
    val response = assistant("What's the square root of 144?")
    println(response)
}