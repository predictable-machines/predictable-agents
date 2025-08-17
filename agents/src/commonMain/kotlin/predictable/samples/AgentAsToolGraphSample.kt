@file:Suppress("unused")
@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package predictable.samples

import kotlinx.serialization.Serializable
import predictable.Agent
import predictable.Tool
import predictable.AI
import predictable.agent.Model

/**
 * Example demonstrating how agents can be used as tools and combined into a powerful graph
 * since they all implement the AI<A, B> interface.
 * 
 * This shows how to create a complex workflow where multiple agents and tools
 * work together to accomplish sophisticated tasks.
 */
@DocumentationSample
suspend fun agentAsToolGraphExample() {
    // Define data structures for our workflow
    @Serializable
    data class ResearchQuery(val topic: String, val depth: String = "comprehensive")
    
    @Serializable
    data class ResearchResults(
        val summary: String,
        val keyFindings: List<String>,
        val sources: List<String>
    )
    
    @Serializable
    data class AnalysisRequest(
        val data: ResearchResults,
        val perspective: String = "technical"
    )
    
    @Serializable
    data class AnalysisReport(
        val insights: List<String>,
        val recommendations: List<String>,
        val riskFactors: List<String>
    )
    
    @Serializable
    data class ContentRequest(
        val research: ResearchResults,
        val analysis: AnalysisReport,
        val format: String = "article"
    )
    
    @Serializable
    data class FinalContent(
        val title: String,
        val content: String,
        val metadata: Map<String, String>
    )
    
    // Create specialized agents for different tasks
    val researchAgent = Agent(
        name = "researcher",
        description = "Conducts in-depth research on topics",
        system = """You are an expert researcher who gathers comprehensive information.
                   |Focus on finding reliable sources and key insights.
                   |Structure your findings clearly.""".trimMargin(),
        model = Model.default
    )
    
    val analysisAgent = Agent(
        name = "analyst",
        description = "Analyzes research data and provides insights",
        system = """You are a strategic analyst who identifies patterns and implications.
                   |Provide actionable insights and risk assessments.
                   |Consider multiple perspectives.""".trimMargin(),
        model = Model.default
    )
    
    val writerAgent = Agent(
        name = "writer",
        description = "Creates polished content from research and analysis",
        system = """You are a professional writer who creates engaging content.
                   |Synthesize research and analysis into clear, compelling narratives.
                   |Adapt your style to the requested format.""".trimMargin(),
        model = Model.default
    )
    
    // Convert agents to strongly-typed tools using the invoke operator
    val researchTool: Tool<ResearchQuery, ResearchResults> = researchAgent()
    val analysisTool: Tool<AnalysisRequest, AnalysisReport> = analysisAgent()
    val writerTool: Tool<ContentRequest, FinalContent> = writerAgent()
    
    // Create a composite tool that chains the agents together
    val contentPipeline = Tool<ResearchQuery, FinalContent>(
        name = "content_pipeline",
        description = "Complete pipeline from research query to final content"
    ) { query ->
        // Step 1: Research
        val research = researchTool(query)
        println("✓ Research completed: ${research.keyFindings.size} findings")
        
        // Step 2: Analysis
        val analysis = analysisTool(AnalysisRequest(research))
        println("✓ Analysis completed: ${analysis.insights.size} insights")
        
        // Step 3: Content Creation
        val content = writerTool(ContentRequest(research, analysis))
        println("✓ Content created: ${content.title}")
        
        content
    }
    
    // Use the pipeline
    val result = contentPipeline(
        ResearchQuery(
            topic = "The impact of AI on software development",
            depth = "comprehensive"
        )
    )
    
    println("\nFinal Output:")
    println("Title: ${result.title}")
    println("Content length: ${result.content.length} characters")
}

/**
 * Example showing a more complex graph with parallel processing and conditional routing
 */
@DocumentationSample
suspend fun complexAgentGraphExample() {
    @Serializable
    data class Document(val content: String, val language: String, val type: String)
    
    @Serializable
    data class TranslatedDocument(val content: String, val originalLanguage: String, val targetLanguage: String)
    
    @Serializable
    data class Summary(val summary: String, val keyPoints: List<String>)
    
    @Serializable
    data class ProcessedDocument(
        val original: Document,
        val translations: Map<String, TranslatedDocument>,
        val summary: Summary,
        val category: String
    )
    
    // Create specialized agents
    val translatorAgent = Agent(
        name = "translator",
        description = "Translates documents between languages",
        system = "You are a professional translator. Maintain accuracy and tone.",
        model = Model.default
    )
    
    val summarizerAgent = Agent(
        name = "summarizer",
        description = "Creates concise summaries of documents",
        system = "You create clear, concise summaries highlighting key points.",
        model = Model.default
    )
    
    val classifierAgent = Agent(
        name = "classifier",
        description = "Classifies documents into categories",
        system = "You classify documents accurately based on their content.",
        model = Model.default
    )
    
    // Convert to tools with specific types
    val translator: AI<Pair<Document, String>, TranslatedDocument> = 
        translatorAgent<Pair<Document, String>, TranslatedDocument>()
    
    val summarizer: AI<Document, Summary> = 
        summarizerAgent<Document, Summary>()
    
    val classifier: AI<Document, String> = 
        classifierAgent<Document, String>()
    
    // Create a document processing pipeline
    val documentProcessor = Tool<Document, ProcessedDocument>(
        name = "document_processor",
        description = "Processes documents with translation, summarization, and classification"
    ) { document ->
        // Parallel processing using coroutines
        val translations = mutableMapOf<String, TranslatedDocument>()
        
        // Conditionally translate based on source language
        if (document.language != "en") {
            translations["en"] = translator(document to "en")
        }
        if (document.language != "es") {
            translations["es"] = translator(document to "es")
        }
        
        // Summarize the document
        val summary = summarizer(document)
        
        // Classify the document
        val category = classifier(document)
        
        ProcessedDocument(
            original = document,
            translations = translations,
            summary = summary,
            category = category
        )
    }
    
    // Use the processor
    val processed = documentProcessor(
        Document(
            content = "This is a technical document about AI...",
            language = "en",
            type = "technical"
        )
    )
    
    println("Document processed:")
    println("- Category: ${processed.category}")
    println("- Translations: ${processed.translations.keys}")
    println("- Key points: ${processed.summary.keyPoints.size}")
}

/**
 * Example demonstrating dynamic agent composition based on runtime conditions
 */
@DocumentationSample
suspend fun dynamicAgentCompositionExample() {
    @Serializable
    data class Task(val type: String, val data: String, val priority: String)
    
    @Serializable
    data class TaskResult(val success: Boolean, val output: String, val processingPath: List<String>)
    
    // Create a registry of specialized agents
    val agentRegistry = mapOf(
        "code_review" to Agent(
            name = "code_reviewer",
            description = "Reviews code for quality and issues",
            system = "You are an expert code reviewer. Focus on best practices and potential bugs.",
            model = Model.default
        ),
        "documentation" to Agent(
            name = "doc_writer",
            description = "Writes technical documentation",
            system = "You write clear, comprehensive technical documentation.",
            model = Model.default
        ),
        "testing" to Agent(
            name = "test_generator",
            description = "Generates test cases",
            system = "You generate comprehensive test cases covering edge cases.",
            model = Model.default
        )
    )
    
    // Create a router that selects agents based on task type
    val taskRouter = Tool<Task, TaskResult>(
        name = "task_router",
        description = "Routes tasks to appropriate agents based on type and priority"
    ) { task ->
        val processingPath = mutableListOf<String>()
        var output = task.data
        
        // Select primary agent based on task type
        val primaryAgent = agentRegistry[task.type]
        if (primaryAgent != null) {
            val tool: AI<String, String> = primaryAgent()
            output = tool(output)
            processingPath.add(primaryAgent.name)
        }
        
        // Add additional processing for high-priority tasks
        if (task.priority == "high") {
            // Always add documentation for high-priority tasks
            val docAgent = agentRegistry["documentation"]
            if (docAgent != null && task.type != "documentation") {
                val docTool: AI<String, String> = docAgent()
                output = "$output\n\nDocumentation:\n${docTool(output)}"
                processingPath.add(docAgent.name)
            }
        }
        
        TaskResult(
            success = true,
            output = output,
            processingPath = processingPath
        )
    }
    
    // Process different types of tasks
    val codeReviewResult = taskRouter(
        Task(
            type = "code_review",
            data = "fun calculateSum(a: Int, b: Int) = a + b",
            priority = "high"
        )
    )
    
    println("Task completed via: ${codeReviewResult.processingPath.joinToString(" -> ")}")
}

/**
 * Example showing how to create a feedback loop with agents
 */
@DocumentationSample
suspend fun agentFeedbackLoopExample() {
    @Serializable
    data class Draft(val content: String, val version: Int)
    
    @Serializable
    data class Review(val approved: Boolean, val feedback: String, val suggestions: List<String>)
    
    @Serializable
    data class FinalDocument(val content: String, val iterations: Int, val reviews: List<Review>)
    
    val writerAgent = Agent(
        name = "writer",
        description = "Writes and revises content",
        system = "You write and improve content based on feedback.",
        model = Model.default
    )
    
    val reviewerAgent = Agent(
        name = "reviewer",
        description = "Reviews content and provides feedback",
        system = "You provide constructive feedback to improve content quality.",
        model = Model.default
    )
    
    // Convert agents to tools
    val writer: AI<Pair<String, String?>, Draft> = writerAgent()
    val reviewer: AI<Draft, Review> = reviewerAgent()
    
    // Create an iterative refinement tool
    val refinementTool = Tool<String, FinalDocument>(
        name = "iterative_refinement",
        description = "Iteratively refines content through writer-reviewer feedback loop"
    ) { initialPrompt ->
        var currentDraft = writer(initialPrompt to null)
        val reviews = mutableListOf<Review>()
        var iterations = 0
        val maxIterations = 3
        
        while (iterations < maxIterations) {
            // Get review
            val review = reviewer(currentDraft)
            reviews.add(review)
            
            if (review.approved) {
                break
            }
            
            // Revise based on feedback
            currentDraft = writer(currentDraft.content to review.feedback)
            iterations++
        }
        
        FinalDocument(
            content = currentDraft.content,
            iterations = iterations + 1,
            reviews = reviews
        )
    }
    
    val finalDoc = refinementTool("Write a brief introduction to Kotlin coroutines")
    println("Document finalized after ${finalDoc.iterations} iterations")
    println("Reviews: ${finalDoc.reviews.map { it.approved }}")
}

/**
 * Example demonstrating how agents and tools can be mixed in a single workflow
 */
@DocumentationSample
suspend fun mixedAgentToolWorkflowExample() {
    // Simple calculation tool (not an agent)
    val calculator = Tool<Pair<Double, Double>, Double>(
        name = "calculator",
        description = "Performs mathematical calculations"
    ) { (a, b) ->
        a + b // Simple addition for this example
    }
    
    // Data validation tool
    val validator = Tool<String, Boolean>(
        name = "validator",
        description = "Validates input data"
    ) { input ->
        input.isNotBlank() && input.length > 10
    }
    
    // Agent for complex reasoning
    val reasoningAgent = Agent(
        name = "reasoner",
        description = "Performs complex reasoning tasks",
        system = "You analyze data and provide logical conclusions.",
        model = Model.default
    )
    
    @Serializable
    data class WorkflowInput(val text: String, val numbers: List<Double>)
    
    @Serializable
    data class WorkflowOutput(
        val isValid: Boolean,
        val sum: Double,
        val reasoning: String
    )
    
    // Combine tools and agents in a workflow
    val workflow = Tool<WorkflowInput, WorkflowOutput>(
        name = "mixed_workflow",
        description = "Workflow combining simple tools and AI agents"
    ) { input ->
        // Step 1: Validate input using simple tool
        val isValid = validator(input.text)
        
        // Step 2: Calculate sum using simple tool
        val sum = input.numbers.reduce { acc, n -> 
            calculator(acc to n)
        }
        
        // Step 3: Use agent for reasoning
        val reasoner: AI<String, String> = reasoningAgent()
        val reasoning = reasoner(
            "Analyze this data: text='${input.text}', sum=$sum, valid=$isValid"
        )
        
        WorkflowOutput(
            isValid = isValid,
            sum = sum,
            reasoning = reasoning
        )
    }
    
    // Execute the workflow
    val result = workflow(
        WorkflowInput(
            text = "This is a sample input text for validation",
            numbers = listOf(1.5, 2.5, 3.0)
        )
    )
    
    println("Workflow completed:")
    println("- Valid: ${result.isValid}")
    println("- Sum: ${result.sum}")
    println("- Reasoning: ${result.reasoning}")
}