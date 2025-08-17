@file:OptIn(ExperimentalUuidApi::class)
@file:Suppress("unused")

package predictable.samples

import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.delay
import predictable.ToolWithEvents
import predictable.tool.KotlinSchema
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// Data classes for the first sample
data class LongTask(val data: String, val steps: Int)
data class Result(val success: Boolean, val output: String, val processedSteps: Int)
data class ProgressEvent(val step: Int, val totalSteps: Int, val message: String)

/**
 * Sample demonstrating how to create a ToolWithEvents for progress tracking
 */
@DocumentationSample
suspend fun toolWithEventsProgressSample() {
    val progressFlow = channelFlow<ProgressEvent> {
        val progressTool = ToolWithEvents<LongTask, Result, ProgressEvent>(
            name = "longTaskWithProgress",
            description = "Executes a long task with progress updates",
            schema = KotlinSchema<LongTask, Result>(),
            id = Uuid.random().toString(),
            scope = this,
            block = { input ->
                for (step in 1..input.steps) {
                    send(ProgressEvent(step, input.steps, "Processing step $step"))
                    // Simulate work
                    delay(100)
                }
                Result(
                    success = true,
                    output = "Processed: ${input.data}",
                    processedSteps = input.steps
                )
            }
        )
        
        // Execute the tool
        val result = progressTool(LongTask("important data", 5))
        println("Final result: ${result.output}")
    }
    
    // Collect and display progress events
    progressFlow.collect { event ->
        println("Progress: ${event.step}/${event.totalSteps} - ${event.message}")
    }
}

// Data classes for the second sample
data class FileProcessing(val fileName: String, val operation: String)
data class ProcessingResult(val success: Boolean, val message: String)
sealed class FileEvent {
    data class Started(val fileName: String) : FileEvent()
    data class Progress(val percentage: Int) : FileEvent()
    data class Completed(val fileName: String) : FileEvent()
    data class Error(val error: String) : FileEvent()
}

/**
 * Sample demonstrating ToolWithEvents using the extension function syntax
 */
@DocumentationSample
suspend fun toolWithEventsExtensionSample() {
    val eventFlow = channelFlow<FileEvent> {
        // Using the extension function syntax
        val fileProcessor = ToolWithEvents<FileProcessing, ProcessingResult, FileEvent>(
            name = "fileProcessor",
            description = "Processes files with event emission",
            schema = KotlinSchema<FileProcessing, ProcessingResult>(),
            id = Uuid.random().toString(),
            scope = this,
            block = { input ->
                send(FileEvent.Started(input.fileName))
                
                try {
                    // Simulate file processing
                    for (progress in listOf(25, 50, 75, 100)) {
                        send(FileEvent.Progress(progress))
                        delay(100)
                    }
                    
                    send(FileEvent.Completed(input.fileName))
                    ProcessingResult(true, "Successfully processed ${input.fileName}")
                } catch (e: Exception) {
                    send(FileEvent.Error(e.message ?: "Unknown error"))
                    ProcessingResult(false, "Failed to process ${input.fileName}")
                }
            }
        )
        
        val result = fileProcessor(FileProcessing("data.csv", "analyze"))
        println("Processing result: ${result.message}")
    }
    
    eventFlow.collect { event ->
        when (event) {
            is FileEvent.Started -> println("Started processing: ${event.fileName}")
            is FileEvent.Progress -> println("Progress: ${event.percentage}%")
            is FileEvent.Completed -> println("Completed: ${event.fileName}")
            is FileEvent.Error -> println("Error: ${event.error}")
        }
    }
}