package com.predictable.machines.build.logic

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.*
import org.gradle.kotlin.dsl.support.zipTo
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.File

/**
 * Gradle task that extracts Kotlin code snippets from README.md and compiles them
 * to ensure all documentation examples are valid and up-to-date.
 */
abstract class CompileReadmeSnippets : DefaultTask() {
    
    @get:InputFile
    abstract val readmeFile: RegularFileProperty
    
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty
    
    @get:Input
    abstract val sourceCompatibility: org.gradle.api.provider.Property<String>
    
    init {
        group = "documentation"
        description = "Extracts and validates Kotlin code snippets from README.md"
    }
    
    @TaskAction
    fun execute() {
        val readme = readmeFile.get().asFile
        val output = outputDir.get().asFile
        
        if (!readme.exists()) {
            logger.warn("README.md not found at ${readme.absolutePath}")
            return
        }
        
        // Clean output directory
        output.deleteRecursively()
        output.mkdirs()
        
        val content = readme.readText()
        val snippets = extractKotlinSnippets(content)
        
        logger.lifecycle("Found ${snippets.size} Kotlin code snippets in README.md")
        
        snippets.forEachIndexed { index, snippet ->
            val fileName = "ReadmeSnippet_${index.toString().padStart(3, '0')}.kt"
            val file = File(output, fileName)
            
            val compilableCode = prepareSnippetForCompilation(snippet, index)
            file.writeText(compilableCode)
            
            logger.debug("Created snippet file: $fileName")
        }
        
        logger.lifecycle("Extracted ${snippets.size} code snippets to ${output.absolutePath}")
    }
    
    /**
     * Extracts Kotlin code blocks from markdown content
     */
    private fun extractKotlinSnippets(content: String): List<String> {
        val pattern = """```kotlin\n(.*?)\n```""".toRegex(RegexOption.DOT_MATCHES_ALL)
        return pattern.findAll(content)
            .map { it.groupValues[1] }
            .filter { it.isNotBlank() }
            .toList()
    }
    
    /**
     * Prepares a code snippet for compilation by adding necessary context
     */
    private fun prepareSnippetForCompilation(snippet: String, index: Int): String {
        val lines = snippet.lines()
        
        // Skip non-Kotlin code (like gradle configuration)
        if (lines.any { it.trim().startsWith("dependencies {") || 
                        it.trim().startsWith("kotlin {") ||
                        it.trim().startsWith("git ") ||
                        it.trim().startsWith("./gradlew ") ||
                        it.trim().startsWith("export ") }) {
            // Return a no-op function for non-Kotlin snippets
            return """
                |// README snippet #$index (non-Kotlin code - skipped)
                |package readme.snippets
                |fun readmeSnippet${index}_skipped() {}
            """.trimMargin()
        }
        
        // Check if this is already a complete file (has package declaration)
        val hasPackage = lines.any { it.trim().startsWith("package ") }
        val hasImports = lines.any { it.trim().startsWith("import ") }
        
        // Extract imports if present
        val imports = if (hasImports) {
            lines.filter { it.trim().startsWith("import ") }
        } else {
            emptyList()
        }
        
        // Get the main content (excluding imports)
        val mainContent = lines.filterNot { it.trim().startsWith("import ") }
            .joinToString("\n")
        
        // Check if this snippet needs to be wrapped in a function
        // We'll wrap anything with val/var declarations at top level to avoid conflicts
        val hasTopLevelVals = mainContent.lines().any { line ->
            line.trimStart().startsWith("val ") || line.trimStart().startsWith("var ")
        }
        val needsWrapper = !hasPackage && (!containsTopLevelDeclaration(mainContent) || hasTopLevelVals)
        
        return buildString {
            // File header
            appendLine("// README snippet #$index")
            appendLine("@file:Suppress(\"unused\", \"UNUSED_VARIABLE\", \"UNUSED_PARAMETER\", \"RedundantSuppression\")")
            appendLine()
            
            // Package declaration
            if (!hasPackage) {
                appendLine("package readme.snippets")
                appendLine()
            }
            
            // Add any explicit imports from the snippet first
            imports.forEach { importLine ->
                appendLine(importLine)
            }
            
            // Then add standard imports for README examples (avoiding duplicates)
            if (!hasPackage) {
                if (!imports.any { it.contains("predictable.") && !it.contains("predictable.agent") && !it.contains("predictable.tool") }) {
                    appendLine("import predictable.*")
                }
                // Always import Agent explicitly if not already present
                if (!imports.any { it.contains("predictable.Agent") }) {
                    appendLine("import predictable.Agent")
                }
                if (!imports.any { it.contains("predictable.agent") }) {
                    appendLine("import predictable.agent.*")
                }
                if (!imports.any { it.contains("predictable.tool") }) {
                    appendLine("import predictable.tool.*")
                }
                if (!imports.any { it.contains("kotlinx.serialization.Serializable") }) {
                    appendLine("import kotlinx.serialization.Serializable")
                }
                if (!imports.any { it.contains("kotlinx.coroutines.flow") }) {
                    appendLine("import kotlinx.coroutines.flow.*")
                }
                if (!imports.any { it.contains("kotlin.uuid.ExperimentalUuidApi") }) {
                    appendLine("import kotlin.uuid.ExperimentalUuidApi")
                }
                appendLine()
            }
            
            // Add the content
            if (needsWrapper) {
                appendLine("@OptIn(ExperimentalUuidApi::class)")
                appendLine("suspend fun readmeSnippet$index() {")
                mainContent.lines().forEach { line ->
                    appendLine("    $line")
                }
                appendLine("}")
            } else {
                // For top-level declarations, add OptIn if needed
                if (mainContent.contains("Uuid") || mainContent.contains("ToolWithEvents")) {
                    appendLine("@OptIn(ExperimentalUuidApi::class)")
                }
                append(mainContent)
            }
        }
    }
    
    /**
     * Checks if the content contains top-level declarations
     * Note: val/var are handled specially to avoid conflicts
     */
    private fun containsTopLevelDeclaration(content: String): Boolean {
        val topLevelPatterns = listOf(
            "^\\s*class\\s+",
            "^\\s*data\\s+class\\s+",
            "^\\s*interface\\s+",
            "^\\s*fun\\s+",
            "^\\s*suspend\\s+fun\\s+",
            "^\\s*object\\s+",
            "^\\s*enum\\s+class\\s+"
        )
        
        return content.lines().any { line ->
            topLevelPatterns.any { pattern ->
                line.matches(Regex(pattern + ".*"))
            }
        }
    }
}