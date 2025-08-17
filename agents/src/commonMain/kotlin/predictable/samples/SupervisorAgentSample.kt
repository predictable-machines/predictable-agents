@file:Suppress("unused")
@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package predictable.samples

import kotlinx.serialization.Serializable
import predictable.Agent
import predictable.Tool
import predictable.AI
import predictable.agent.Model

/**
 * Example demonstrating how to use the Agent.invoke() operator to convert
 * multiple specialized agents into tools and add them to a supervising agent.
 * 
 * This pattern enables powerful multi-agent systems where a supervisor
 * coordinates the work of specialist agents.
 */
@DocumentationSample
suspend fun supervisorAgentWithToolsExample() {
    // Define data structures for our multi-agent system
    @Serializable
    data class ProjectRequest(
        val description: String,
        val requirements: List<String>,
        val techStack: String
    )
    
    @Serializable
    data class CodeStructure(
        val architecture: String,
        val modules: List<String>,
        val dependencies: List<String>
    )
    
    @Serializable
    data class Implementation(
        val code: Map<String, String>, // filename -> code
        val explanation: String
    )
    
    @Serializable
    data class TestSuite(
        val tests: Map<String, String>, // test name -> test code
        val coverage: String
    )
    
    @Serializable
    data class Documentation(
        val readme: String,
        val apiDocs: Map<String, String>
    )
    
    @Serializable
    data class CompletedProject(
        val structure: CodeStructure,
        val implementation: Implementation,
        val tests: TestSuite,
        val documentation: Documentation,
        val summary: String
    )
    
    // Create specialized agents for different aspects of software development
    val architectAgent = Agent(
        name = "architect",
        description = "Designs software architecture and structure",
        system = """You are a software architect who designs clean, scalable architectures.
                   |Focus on best practices, design patterns, and maintainability.
                   |Output detailed architecture plans.""".trimMargin(),
        model = Model.default
    )
    
    val coderAgent = Agent(
        name = "coder",
        description = "Implements code based on architecture",
        system = """You are an expert programmer who writes clean, efficient code.
                   |Follow the architecture strictly and use best practices.
                   |Include helpful comments and ensure code quality.""".trimMargin(),
        model = Model.default
    )
    
    val testerAgent = Agent(
        name = "tester",
        description = "Creates comprehensive test suites",
        system = """You are a QA engineer who writes thorough test cases.
                   |Cover edge cases, write unit and integration tests.
                   |Ensure high code coverage and test quality.""".trimMargin(),
        model = Model.default
    )
    
    val documentorAgent = Agent(
        name = "documentor",
        description = "Creates project documentation",
        system = """You are a technical writer who creates clear documentation.
                   |Write user-friendly READMEs and comprehensive API docs.
                   |Include examples and best practices.""".trimMargin(),
        model = Model.default
    )
    
    // Convert agents to strongly-typed tools using the invoke() operator
    val architectTool: Tool<ProjectRequest, CodeStructure> = architectAgent()
    val coderTool: Tool<CodeStructure, Implementation> = coderAgent()
    val testerTool: Tool<Implementation, TestSuite> = testerAgent()
    val documentorTool: Tool<Pair<CodeStructure, Implementation>, Documentation> = documentorAgent()
    
    // Create a supervisor agent that coordinates all the specialist agents
    val supervisorAgent = Agent(
        name = "project_supervisor",
        description = "Supervises and coordinates the entire project development",
        system = """You are a project manager coordinating a software development team.
                   |You have access to specialized agents for architecture, coding, testing, and documentation.
                   |Use these tools to complete the project step by step.
                   |Ensure all components work together cohesively.""".trimMargin(),
        model = Model.default,
        tools = listOf(architectTool, coderTool, testerTool, documentorTool) // Add agent-tools to supervisor
    )
    
    // Use the supervisor to complete a project
    val project = supervisorAgent.generateObject<ProjectRequest, CompletedProject>(
        ProjectRequest(
            description = "A task management API",
            requirements = listOf(
                "RESTful endpoints for CRUD operations",
                "User authentication",
                "Task assignment and tracking"
            ),
            techStack = "Kotlin with Ktor"
        )
    )
    
    println("Project completed by supervisor:")
    println("- Architecture modules: ${project.structure.modules}")
    println("- Implementation files: ${project.implementation.code.keys}")
    println("- Test coverage: ${project.tests.coverage}")
    println("- Documentation created: README and ${project.documentation.apiDocs.size} API docs")
}

/**
 * Example showing a hierarchical multi-agent system with multiple supervisor levels
 */
@DocumentationSample
suspend fun hierarchicalAgentSystemExample() {
    @Serializable
    data class ResearchTask(val topic: String, val depth: String)
    
    @Serializable
    data class ResearchData(val findings: List<String>, val sources: List<String>)
    
    @Serializable
    data class Analysis(val insights: List<String>, val patterns: List<String>)
    
    @Serializable
    data class Report(val executive_summary: String, val detailed_analysis: String)
    
    @Serializable
    data class Presentation(val slides: List<String>, val speaker_notes: String)
    
    @Serializable
    data class ResearchProject(
        val research: ResearchData,
        val analysis: Analysis,
        val report: Report,
        val presentation: Presentation
    )
    
    // Layer 1: Specialist agents
    val dataCollectorAgent = Agent(
        name = "data_collector",
        description = "Collects research data from various sources",
        system = "You gather comprehensive data from reliable sources.",
        model = Model.default
    )
    
    val dataAnalystAgent = Agent(
        name = "data_analyst",
        description = "Analyzes collected data for insights",
        system = "You identify patterns and extract meaningful insights from data.",
        model = Model.default
    )
    
    // Layer 2: Mid-level supervisor for research
    val researchSupervisor = Agent(
        name = "research_supervisor",
        description = "Supervises research and analysis",
        system = """You coordinate research activities.
                   |Use data collection and analysis tools to produce comprehensive research.""".trimMargin(),
        model = Model.default,
        tools = listOf(
            dataCollectorAgent<ResearchTask, ResearchData>(),
            dataAnalystAgent<ResearchData, Analysis>()
        )
    )
    
    // Layer 1: More specialist agents
    val reportWriterAgent = Agent(
        name = "report_writer",
        description = "Writes professional reports",
        system = "You create well-structured, professional reports.",
        model = Model.default
    )
    
    val presentationAgent = Agent(
        name = "presentation_creator",
        description = "Creates engaging presentations",
        system = "You design clear, engaging presentation slides.",
        model = Model.default
    )
    
    // Layer 2: Mid-level supervisor for output generation
    val outputSupervisor = Agent(
        name = "output_supervisor",
        description = "Supervises report and presentation creation",
        system = """You coordinate output generation.
                   |Create professional reports and presentations from analysis.""".trimMargin(),
        model = Model.default,
        tools = listOf(
            reportWriterAgent<Analysis, Report>(),
            presentationAgent<Report, Presentation>()
        )
    )
    
    // Layer 3: Top-level executive supervisor
    val executiveSupervisor = Agent(
        name = "executive_supervisor",
        description = "Executive oversight of entire research project",
        system = """You are the executive supervisor overseeing the entire research project.
                   |Coordinate between research and output teams to deliver complete projects.
                   |Ensure all deliverables meet high quality standards.""".trimMargin(),
        model = Model.default,
        tools = listOf(
            researchSupervisor<ResearchTask, Pair<ResearchData, Analysis>>(),
            outputSupervisor<Analysis, Pair<Report, Presentation>>()
        )
    )
    
    // Execute a complex research project through the hierarchy
    val project = executiveSupervisor.generateObject<ResearchTask, ResearchProject>(
        ResearchTask(
            topic = "Impact of AI on Software Development",
            depth = "comprehensive"
        )
    )
    
    println("Hierarchical execution completed:")
    println("- Research findings: ${project.research.findings.size}")
    println("- Analysis insights: ${project.analysis.insights.size}")
    println("- Report sections: Executive Summary + Detailed Analysis")
    println("- Presentation slides: ${project.presentation.slides.size}")
}

/**
 * Example showing dynamic agent tool composition based on task requirements
 */
@DocumentationSample
suspend fun dynamicAgentToolCompositionExample() {
    @Serializable
    data class DynamicTask(
        val type: String,
        val complexity: String,
        val requirements: List<String>,
        val data: String
    )
    
    @Serializable
    data class TaskResult(
        val output: String,
        val agents_used: List<String>,
        val quality_score: Double
    )
    
    // Create a pool of specialist agents
    val agentPool = mapOf(
        "translator" to Agent(
            name = "translator",
            description = "Translates content between languages",
            system = "You are a professional translator.",
            model = Model.default
        ),
        "summarizer" to Agent(
            name = "summarizer",
            description = "Creates concise summaries",
            system = "You create clear, concise summaries.",
            model = Model.default
        ),
        "analyzer" to Agent(
            name = "analyzer",
            description = "Performs deep analysis",
            system = "You provide detailed analysis.",
            model = Model.default
        ),
        "optimizer" to Agent(
            name = "optimizer",
            description = "Optimizes content and processes",
            system = "You optimize for efficiency and quality.",
            model = Model.default
        )
    )
    
    // Function to dynamically compose agents based on task requirements
    fun composeAgentsForTask(task: DynamicTask): List<AI<*, *>> {
        val selectedTools = mutableListOf<AI<*, *>>()
        
        // Add agents based on requirements
        if ("translation" in task.requirements) {
            selectedTools.add(agentPool["translator"]!!<String, String>())
        }
        if ("summary" in task.requirements) {
            selectedTools.add(agentPool["summarizer"]!!<String, String>())
        }
        if (task.complexity == "high") {
            selectedTools.add(agentPool["analyzer"]!!<String, String>())
        }
        if ("optimization" in task.requirements) {
            selectedTools.add(agentPool["optimizer"]!!<String, String>())
        }
        
        return selectedTools
    }
    
    // Create dynamic supervisor that adapts its tools based on the task
    fun createDynamicSupervisor(task: DynamicTask): Agent {
        val tools = composeAgentsForTask(task)
        
        return Agent(
            name = "dynamic_supervisor",
            description = "Dynamically adapts to task requirements",
            system = """You are an adaptive supervisor that uses the right tools for each task.
                       |Available tools: ${tools.map { it.name }}.
                       |Use them wisely to complete the task efficiently.""".trimMargin(),
            model = Model.default,
            tools = tools // Dynamically composed agent-tools
        )
    }
    
    // Example task requiring multiple capabilities
    val complexTask = DynamicTask(
        type = "content_processing",
        complexity = "high",
        requirements = listOf("translation", "summary", "optimization"),
        data = "Complex multilingual content that needs processing..."
    )
    
    // Create supervisor with dynamically selected tools
    val dynamicSupervisor = createDynamicSupervisor(complexTask)
    
    // Process the task
    val result = dynamicSupervisor.generateObject<DynamicTask, TaskResult>(complexTask)
    
    println("Dynamic task completed:")
    println("- Agents used: ${result.agents_used}")
    println("- Quality score: ${result.quality_score}")
}

/**
 * Example showing agent tools with feedback and iteration
 */
@DocumentationSample
suspend fun agentToolsWithFeedbackExample() {
    @Serializable
    data class Draft(val content: String, val version: Int)
    
    @Serializable
    data class Review(
        val approved: Boolean,
        val score: Double,
        val feedback: String,
        val suggestions: List<String>
    )
    
    @Serializable
    data class Revision(val draft: Draft, val changes_made: List<String>)
    
    @Serializable
    data class FinalOutput(
        val content: String,
        val iterations: Int,
        val final_score: Double,
        val revision_history: List<Revision>
    )
    
    // Create specialist agents
    val writerAgent = Agent(
        name = "writer",
        description = "Writes and revises content",
        system = "You write high-quality content and revise based on feedback.",
        model = Model.default
    )
    
    val editorAgent = Agent(
        name = "editor",
        description = "Reviews and provides feedback",
        system = "You provide constructive feedback to improve content quality.",
        model = Model.default
    )
    
    val qualityAgent = Agent(
        name = "quality_checker",
        description = "Ensures quality standards",
        system = "You verify content meets quality standards.",
        model = Model.default
    )
    
    // Create an iterative supervisor with feedback loop
    val iterativeSupervisor = Agent(
        name = "iterative_supervisor",
        description = "Manages iterative content refinement",
        system = """You manage an iterative refinement process.
                   |Use the writer to create drafts, editor for feedback, and quality checker for validation.
                   |Iterate until quality standards are met (max 3 iterations).""".trimMargin(),
        model = Model.default,
        tools = listOf(
            writerAgent<Pair<String, String?>, Draft>(),     // (prompt, feedback) -> Draft
            editorAgent<Draft, Review>(),                     // Draft -> Review
            qualityAgent<Draft, Double>()                     // Draft -> Quality Score
        )
    )
    
    // Process content with iterative refinement
    val finalContent = iterativeSupervisor.generateObject<String, FinalOutput>(
        "Write a technical blog post about Kotlin coroutines"
    )
    
    println("Iterative refinement completed:")
    println("- Final score: ${finalContent.final_score}")
    println("- Iterations: ${finalContent.iterations}")
    println("- Revisions made: ${finalContent.revision_history.size}")
}