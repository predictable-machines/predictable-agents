# Predictable Agents

> A type-safe, multiplatform Kotlin library for building predictable AI-powered applications with functional programming

[![Kotlin](https://img.shields.io/badge/kotlin-2.1.0-blue.svg?logo=kotlin)](http://kotlinlang.org)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Multiplatform](https://img.shields.io/badge/Kotlin-Multiplatform-blueviolet)](https://kotlinlang.org/docs/multiplatform.html)

## Features

- 🛡️ **Type-Safe AI Interactions** - Automatic schema generation from Kotlin data classes
- 🌍 **Multiplatform Support** - Works on JVM, Android, iOS, Linux, and WASM
- 🔧 **Tool Integration** - Extend AI capabilities with custom tools and functions
- 🔄 **Agent Composition** - Agents can be used as tools, enabling powerful processing graphs
- 🚀 **Streaming Support** - Real-time response generation for interactive applications
- 🎯 **Functional Programming** - Built with Arrow-kt for composable, predictable behavior
- 🔌 **Provider Flexibility** - Compatible with OpenAI, OpenRouter, and custom endpoints
- 📊 **Event Tracking** - Monitor progress of long-running operations

## Installation

Add the following to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.predictablemachines:agents:0.1.0-SNAPSHOT")
    
    // Optional: MCP support
    implementation("com.predictablemachines:mcp:0.1.0-SNAPSHOT")
}
```

### Multiplatform Setup

```kotlin
kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("com.predictablemachines:agents:0.1.0-SNAPSHOT")
            }
        }
    }
}
```

### Configuration

Set up your API keys as environment variables:

```bash
export OPENAI_API_KEY="your-api-key"
# Optional: For OpenRouter
export OPENROUTER_API_KEY="your-openrouter-key"
# Optional: Custom API endpoint
export OPENAI_API_URL="https://api.openai.com/v1/"
```

## Quick Start

### Basic Text Generation

```kotlin
import predictable.Agent
import predictable.agent.Model

suspend fun main() {
    val agent = Agent(
        name = "assistant",
        description = "A helpful AI assistant",
        system = "You are a helpful assistant that provides clear and concise answers.",
        model = Model.defaultModel // Uses gpt-4o-mini by default
    )
    
    // Simple text generation using invoke operator
    val response = agent("What is Kotlin Multiplatform?")
    println(response)
}
```

### Type-Safe Structured Generation

```kotlin
import kotlinx.serialization.Serializable
import predictable.Agent
import predictable.agent.Model

@Serializable
data class Recipe(
    val name: String,
    val ingredients: List<String>,
    val instructions: List<String>,
    val prepTime: Int,
    val servings: Int
)

suspend fun generateRecipe() {
    val agent = Agent(
        name = "chef",
        description = "Expert culinary AI",
        system = "You are a professional chef who creates detailed recipes.",
        model = Model.defaultModel
    )
    
    val recipe = agent.generateObject<String, Recipe>(
        "Create a recipe for chocolate chip cookies"
    )
    
    println("Recipe: ${recipe.name}")
    println("Prep time: ${recipe.prepTime} minutes")
    recipe.ingredients.forEach { println("- $it") }
}
```

### Streaming Responses

```kotlin
import kotlinx.coroutines.flow.collect
import predictable.Agent
import predictable.agent.Model
import predictable.agent.StreamResponse

suspend fun streamStory() {
    val agent = Agent(
        name = "storyteller",
        description = "Creative story writer",
        system = "You are a creative writer who tells engaging stories.",
        model = Model.defaultModel
    )
    
    agent.stream("Tell me a short story about a robot learning to paint")
        .collect { response ->
            when (response) {
                is StreamResponse.Chunk -> print(response.value)
                else -> {} // Handle other response types if needed
            }
        }
}
```

## Core Concepts

### AI Interface

The `AI<A, B>` interface is the foundation of the library, representing any transformation from input type `A` to output type `B`. It defines the core contract that all AI operations must implement, including agents and tools.

### Agent

An `Agent` is a high-level AI assistant that can generate text, structured data, and use tools:

```kotlin
val agent = Agent(
    name = "assistant",
    description = "General purpose AI assistant",
    system = "System prompt defining behavior",
    model = Model(
        apiUrl = "https://api.openai.com/v1/",
        name = "gpt-4o"
    ),
    tools = emptyList() // Optional: Add tools here
)
```

### Tools

Tools extend agent capabilities by wrapping functions as AI-compatible operations:

```kotlin
import predictable.Tool
import predictable.Agent
import predictable.agent.Model
import kotlinx.serialization.Serializable

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
suspend fun useWeatherTool() {
    val agent = Agent(
        name = "weather_assistant",
        description = "Assistant for weather queries",
        system = "You are a helpful weather assistant.",
        model = Model.defaultModel,
        tools = listOf(weatherTool)
    )

    val response = agent.chat("What's the weather in London?")
    println(response.value)
}
```

### Tools with Events

For long-running operations with progress tracking:

```kotlin
import predictable.ToolWithEvents
import predictable.tool.KotlinSchema
import kotlinx.serialization.Serializable
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Serializable
data class ProcessRequest(val files: List<String>)

@Serializable
data class ProcessResult(val processed: Int, val failed: Int)

@OptIn(ExperimentalUuidApi::class)
suspend fun toolWithEventsExample() {
    // ToolWithEvents must be created within a channelFlow scope
    val eventFlow = channelFlow<String> {
        val processingTool = ToolWithEvents<ProcessRequest, ProcessResult, String>(
            name = "process_files",
            description = "Process multiple files with progress tracking",
            schema = KotlinSchema<ProcessRequest, ProcessResult>(),
            id = Uuid.random().toString(),
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
    }

    // Collect progress events
    eventFlow.collect { event ->
        println(event)
    }
}
```

## Advanced Usage

### Chat Conversations

```kotlin
import predictable.Agent
import predictable.agent.Message
import predictable.agent.Model

val agent = Agent(
    name = "chatbot",
    description = "Conversational AI assistant",
    system = "You are a helpful and friendly assistant.",
    model = Model.defaultModel
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
```

### Streaming Structured Data

```kotlin
import kotlinx.serialization.Serializable
import kotlinx.coroutines.flow.collect
import predictable.Agent
import predictable.agent.Model
import predictable.agent.StreamResponse

@Serializable
data class AnalysisResult(
    val summary: String? = null,
    val score: Double? = null,
    val recommendations: List<String>? = null
)

suspend fun streamAnalysis(data: String) {
    val agent = Agent(
        name = "analyzer",
        description = "Data analysis assistant",
        system = "You are a data analyst who provides detailed insights.",
        model = Model.defaultModel
    )
    
    agent.streamObject<String, AnalysisResult>(data)
        .collect { response ->
            when (response) {
                is StreamResponse.Chunk -> {
                    // Handle partial results as they stream in
                    response.value?.summary?.let { println("Summary: $it") }
                    response.value?.score?.let { println("Score: $it") }
                }
                else -> {} // Handle other response types if needed
            }
        }
}
```

### Custom Model Configuration

```kotlin
import predictable.Agent
import predictable.agent.Model

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
    model = openRouterModel
    // Pass apiKey when creating the agent if needed
)
```

### Tool Composition

```kotlin
import predictable.Tool

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
    model = Model.defaultModel,
    tools = listOf(searchTool, summaryTool)
)
```

## Agent Graphs and Composition

Since both `Agent` and `Tool` implement the `AI<A, B>` interface, agents can be used as tools and combined into powerful processing graphs. The `invoke()` operator on `Agent` converts it to a strongly-typed `Tool`, enabling sophisticated multi-agent systems.

### Supervisor Agent Pattern

```kotlin
import predictable.Agent
import predictable.Tool
import predictable.agent.Model
import kotlinx.serialization.Serializable

// Define your workflow data structures
@Serializable
data class ProjectRequest(
    val description: String,
    val requirements: List<String>
)

@Serializable
data class ProjectResult(
    val architecture: String,
    val implementation: String,
    val tests: String
)

// Create specialized agents for different tasks
val architectAgent = Agent(
    name = "architect",
    description = "Designs software architecture",
    system = "You are a software architect. Design clean, scalable systems.",
    model = Model.defaultModel
)

val coderAgent = Agent(
    name = "coder",
    description = "Implements code",
    system = "You are a senior developer. Write clean, efficient code.",
    model = Model.defaultModel
)

val testerAgent = Agent(
    name = "tester",
    description = "Creates test suites",
    system = "You are a QA engineer. Write comprehensive tests.",
    model = Model.defaultModel
)

// Convert agents to tools using the invoke() operator
val architectTool: Tool<String, String> = architectAgent()
val coderTool: Tool<String, String> = coderAgent()
val testerTool: Tool<String, String> = testerAgent()

// Create a supervisor agent with specialist agents as tools
val supervisor = Agent(
    name = "project_manager",
    description = "Manages the development team",
    system = """You are a project manager coordinating a development team.
               |Use your specialist tools to complete projects:
               |- architect: For system design
               |- coder: For implementation
               |- tester: For quality assurance""".trimMargin(),
    model = Model.defaultModel,
    tools = listOf(architectTool, coderTool, testerTool)
)

// Use the supervisor to orchestrate the specialists
val result = supervisor.generateObject<ProjectRequest, ProjectResult>(
    ProjectRequest(
        description = "Build a REST API for task management",
        requirements = listOf("CRUD operations", "Authentication", "Validation")
    )
)

println("Architecture: ${result.architecture}")
println("Implementation: ${result.implementation}")
println("Tests: ${result.tests}")
```

### Dynamic Agent Composition

```kotlin
// Dynamically compose agents based on task requirements
fun createAdaptiveSupervisor(capabilities: List<String>): Agent {
    // Create specialist agents as needed
    val tools = capabilities.map { capability ->
        val agent = Agent(
            name = capability,
            description = "Handles $capability tasks",
            system = "You are a $capability specialist.",
            model = Model.defaultModel
        )
        // Convert to tool with invoke()
        agent.invoke<String, String>()
    }
    
    return Agent(
        name = "adaptive_supervisor",
        description = "Adapts to different tasks",
        system = "Coordinate your team of specialists: ${capabilities.joinToString()}",
        model = Model.defaultModel,
        tools = tools
    )
}

// Create a supervisor with custom capabilities
suspend fun useDynamicSupervisor() {
    val customSupervisor = createAdaptiveSupervisor(
        listOf("translator", "summarizer", "analyzer")
    )

    // Use it for complex tasks
    val output = customSupervisor("Translate, summarize, and analyze this document...")
    println(output)
}
```

## Model Context Protocol (MCP) Support

The MCP module enables you to expose your AI agents and tools through the [Model Context Protocol](https://modelcontextprotocol.io/), allowing them to be consumed by any MCP-compatible client.

### Quick Start with MCP Server

```kotlin
import predictable.Tool
import predictable.mcp.server.MCPServer.startKtorMCPServer
import predictable.tool.KotlinSchema
import kotlinx.serialization.Serializable
import kotlinx.coroutines.runBlocking

// Define your tool's input/output types
@Serializable
data class TranslationInput(val text: String, val targetLanguage: String)

@Serializable
data class TranslationOutput(val translatedText: String, val sourceLanguage: String)

fun main() = runBlocking {
    // Create tools to expose via MCP
    val translator = Tool(
        name = "translator",
        description = "Translates text to different languages",
        schema = KotlinSchema(
            TranslationInput.serializer(),
            TranslationOutput.serializer()
        )
    ) { input ->
        // Your translation logic here
        TranslationOutput(
            translatedText = "Translated: ${input.text}",
            sourceLanguage = "auto-detected"
        )
    }
    
    // Start the MCP server with your tools
    val server = startKtorMCPServer(
        tools = listOf(translator),
        port = 8080,
        host = "0.0.0.0"
    )
    
    println("MCP Server running at http://localhost:8080/sse")
    println("Connect with any MCP client to use the tools")
    
    server.start(wait = true)
}
```

### Using MCP with Existing Ktor Applications

```kotlin
import io.ktor.server.application.*
import predictable.mcp.server.MCPServer.configureMCP
import predictable.Tool
import predictable.tool.KotlinSchema
import kotlinx.serialization.Serializable

@Serializable
data class Input(val text: String)

@Serializable  
data class Output(val result: String)

fun Application.module() {
    // Your existing Ktor configuration
    
    // Create your tools
    val translatorTool = Tool(
        name = "translator",
        description = "Translates text",
        schema = KotlinSchema(Input.serializer(), Output.serializer())
    ) { input -> Output("Translated: ${input.text}") }
    
    val calculatorTool = Tool(
        name = "calculator", 
        description = "Calculates results",
        schema = KotlinSchema(Input.serializer(), Output.serializer())
    ) { input -> Output("Calculated: ${input.text}") }
    
    // Add MCP support
    configureMCP(
        tools = listOf(translatorTool, calculatorTool),
        serverName = "my-mcp-server",
        serverVersion = "1.0.0"
    )
}
```

### Connecting as an MCP Client

```kotlin
import predictable.mcp.client.MCPClient
import predictable.mcp.config.MCPConfig
import predictable.mcp.config.MCPServerConfig
import predictable.mcp.config.ServerConfig
import kotlinx.serialization.json.*

suspend fun connectToMCPServer() {
    val config = MCPConfig(
        servers = mapOf(
            "remote-server" to MCPServerConfig(
                name = "Remote Server",
                namespace = "remote",
                description = "Remote MCP server",
                config = ServerConfig.SSE(
                    url = "http://localhost:8080/sse"
                )
            )
        )
    )
    
    MCPClient(config) { client ->
        // List available tools
        val tools = client.tools()
        tools.forEach { tool ->
            println("Found tool: ${tool.name} - ${tool.description}")
        }
        
        // Invoke a tool with JsonObject
        val translatorTool = tools.first { it.name == "translator" }
        val input = buildJsonObject {
            put("text", "Hello world")
            put("targetLanguage", "Spanish")
        }
        val result = translatorTool.invoke(input)
        
        println("Translation result: $result")
    }
}
```

### Exposing Agents as MCP Tools

Since agents implement the `AI` interface, they can be directly exposed through MCP:

```kotlin
import predictable.Agent
import predictable.agent.Model
import predictable.mcp.server.MCPServer.startKtorMCPServer
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val codeReviewer = Agent(
        name = "code_reviewer",
        description = "Reviews code for quality and best practices",
        system = "You are an expert code reviewer. Provide constructive feedback.",
        model = Model.defaultModel
    )

    val testGenerator = Agent(
        name = "test_generator",
        description = "Generates unit tests for code",
        system = "You are a test engineer. Create comprehensive test cases.",
        model = Model.defaultModel
    )

    // Expose agents as MCP tools
    val server = startKtorMCPServer(
        tools = listOf(
            codeReviewer.invoke<String, String>(),  // Convert agent to tool with explicit types
            testGenerator.invoke<String, String>()
        ),
        port = 8080
    )
    
    server.start(wait = true)
}
```

## Examples

### Building a Q&A System

```kotlin
import kotlinx.serialization.Serializable
import predictable.Agent
import predictable.agent.Model

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

class QASystem {
    private val agent = Agent(
        name = "qa_system",
        description = "Question answering system",
        system = """You are an expert Q&A system. 
                   |Provide accurate, well-sourced answers.
                   |Rate your confidence from 0.0 to 1.0.""".trimMargin(),
        model = Model.defaultModel
    )
    
    suspend fun answer(question: Question): Answer {
        return agent.generateObject<Question, Answer>(question)
    }
}
```

### Multi-Agent Collaboration

```kotlin
import predictable.Agent
import predictable.agent.Model

class MultiAgentSystem {
    private val researcher = Agent(
        name = "researcher",
        description = "Research specialist",
        system = "You are a research specialist who gathers information.",
        model = Model.defaultModel
    )
    
    private val analyst = Agent(
        name = "analyst",
        description = "Data analyst",
        system = "You analyze data and provide insights.",
        model = Model.defaultModel
    )
    
    private val writer = Agent(
        name = "writer",
        description = "Content writer",
        system = "You create clear, engaging content from analysis.",
        model = Model.defaultModel
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
```

## API Reference

Full API documentation is available at [docs.predictablemachines.com](https://docs.predictablemachines.com)

### Key Classes

- `AI<A, B>` - Core abstraction for AI operations
- `Agent` - High-level AI assistant with chat and generation capabilities
- `Tool<A, B>` - Wraps functions as AI-compatible tools
- `ToolWithEvents<A, B>` - Tools with progress event emission
- `Model` - AI model configuration
- `Schema<T>` - Type schema representation

### Key Methods

#### Agent
- `generate(prompt: String): String` - Generate text response
- `generateObject<A, B>(input: A): B` - Type-safe structured generation
- `stream(prompt: String): Flow<String>` - Stream text responses
- `streamObject<A, B>(input: A): Flow<B>` - Stream structured data
- `chat(message: String, messages: List<Message>): ChatResponse` - Chat with context

#### Tool
- `invoke(input: A): B` - Execute the tool
- `schema` - Access input/output schemas

## Contributing

We welcome contributions!

### Development Setup

1. Clone the repository:
```bash
git clone https://github.com/predictable-machines/predictable-agents.git
cd predictable-agents
```

2. Build the project:
```bash
./gradlew build
```

3. Run tests:
```bash
./gradlew test
```

### Reporting Issues

Please report issues on our [GitHub Issues](https://github.com/predictable-machines/predictable-agents/issues) page.

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

## Credits

Built with ❤️ by [Predictable Machines](https://predictablemachines.com)

### Dependencies

- [Arrow-kt](https://arrow-kt.io/) - Functional programming for Kotlin
- [OpenAI Kotlin](https://github.com/aallam/openai-kotlin) - OpenAI API client
- [Kotlinx Serialization](https://github.com/Kotlin/kotlinx.serialization) - Serialization support
- [Ktor](https://ktor.io/) - HTTP client
- [Xemantic AI Tool Schema](https://github.com/xemantic/xemantic-ai-tool-schema) - Schema generation

## Related Projects

- [Model Context Protocol (MCP)](https://modelcontextprotocol.io/) - Integration available via `predictable-agents-mcp` module
- [LangChain](https://langchain.com/) - Python/JS alternative for AI applications
- [Semantic Kernel](https://github.com/microsoft/semantic-kernel) - Microsoft's AI orchestration framework
