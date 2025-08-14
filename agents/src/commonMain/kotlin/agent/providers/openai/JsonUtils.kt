package predictable.agent.providers.openai

import com.aallam.openai.api.chat.ChatCompletionChunk
import com.aallam.openai.client.OpenAI
import predictable.agent.RequestParameters
import predictable.agent.StreamResponse
import predictable.tool.Schema
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.FlowCollector
import predictable.tool.Elements
import predictable.tool.KotlinSchema
import predictable.tool.OutputSchema

private val logger = KotlinLogging.logger {}

/**
 * Tries to parse JSON from a content string and emit structured objects
 */
suspend fun <T> FlowCollector<StreamResponse<T>>.tryEmitStructuredObjects(
  it: ChatCompletionChunk,
  buffer: StringBuilder,
  schema: OutputSchema<T>
) {
  val delta = it.choices.firstOrNull()?.delta
  val content = delta?.content

  logger.debug { "tryEmitStructuredObjects: content=$content" }
  logger.debug { "tryEmitStructuredObjects: buffer before=$buffer" }

  // Check if this is the end of the stream
  val isFinishReason = it.choices.firstOrNull()?.finishReason != null
  logger.debug { "tryEmitStructuredObjects: isFinishReason=$isFinishReason" }

  // Try to parse the JSON and emit the result
  val result = tryParseJson(buffer, content, schema) { parsedObject ->
    emitParsedObject(parsedObject)
  }

  logger.debug { "tryEmitStructuredObjects: tryParseJson result=$result" }
  logger.debug { "tryEmitStructuredObjects: buffer after=$buffer" }

  // If this is the end of the stream and we still have content in the buffer,
  // try one more time to parse it
  if (isFinishReason && buffer.isNotEmpty()) {
    logger.debug { "tryEmitStructuredObjects: Final attempt to parse buffer at end of stream" }
    val finalResult = tryParseJson(buffer, "", schema) { parsedObject ->
      emitParsedObject(parsedObject)
    }

    logger.debug { "tryEmitStructuredObjects: Final parse result=$finalResult" }

    // If we still couldn't parse the buffer, log a warning and try to emit it as-is
    if (finalResult == null && buffer.isNotEmpty()) {
      logger.warn { "tryEmitStructuredObjects: WARNING - Could not parse remaining buffer content: $buffer" }

      // Try to clean up the buffer and make it valid JSON
      val cleanedJson = cleanupJson(buffer.toString())
      if (cleanedJson != null) {
        logger.debug { "tryEmitStructuredObjects: Attempting to parse cleaned JSON: $cleanedJson" }
        try {
          val element = schema.outputFromJson(cleanedJson)
          logger.debug { "tryEmitStructuredObjects: Successfully parsed cleaned JSON to $element" }
          emitParsedObject(element)
        } catch (e: Exception) {
          logger.error(e) { "tryEmitStructuredObjects: Error parsing cleaned JSON: ${e.message}" }
        }
      }

      // Clear the buffer regardless of whether we could parse it
      buffer.clear()
    }
  }
}

/**
 * Helper function to emit a parsed object, handling Elements wrappers
 */
private suspend fun <T> FlowCollector<StreamResponse<T>>.emitParsedObject(parsedObject: T) {
  logger.debug { "emitParsedObject: Successfully parsed object: $parsedObject" }

  // Check if the parsed object is an Elements wrapper
  if (parsedObject is Elements<*>) {
    logger.debug { "emitParsedObject: Parsed object is an Elements wrapper with ${(parsedObject as Elements<*>).elements.size} elements" }

    // If it's an Elements wrapper, emit each element individually
    @Suppress("UNCHECKED_CAST")
    (parsedObject as Elements<T>).elements.forEach { element ->
      logger.debug { "emitParsedObject: Emitting element: $element" }
      emit(StreamResponse.Chunk(element))
    }
  } else {
    // If it's not an Elements wrapper, emit it directly
    logger.debug { "emitParsedObject: Emitting parsed object directly" }
    emit(StreamResponse.Chunk(parsedObject))
  }
}

/**
 * Attempts to clean up malformed JSON
 */
fun cleanupJson(json: String): String? {
  // If it's already valid JSON, return it
  if (isValidJson(json)) {
    return json
  }

  // Try to find a valid JSON object in the string
  val candidates = findJsonCandidates(json)
  if (candidates.isNotEmpty()) {
    return candidates.first()
  }

  // If we couldn't find a valid JSON object, try to fix common issues
  val trimmed = json.trim()

  // Check if it's missing a closing brace
  if (trimmed.startsWith("{") && !trimmed.endsWith("}")) {
    val fixed = "$trimmed}"
    if (isValidJson(fixed)) {
      return fixed
    }
  }

  // Check if it's missing an opening brace
  if (!trimmed.startsWith("{") && trimmed.endsWith("}")) {
    val fixed = "{$trimmed"
    if (isValidJson(fixed)) {
      return fixed
    }
  }

  // If we couldn't fix it, return null
  return null
}

/**
 * Tries to parse JSON from a content string
 */
suspend fun <T, A> tryParseJson(
  buffer: StringBuilder,
  content: String?,
  schema: OutputSchema<T>,
  onSuccess: suspend (T) -> A
) : A? {
  logger.debug { "tryParseJson: content=$content" }

  // Append new content to the buffer
  if (content != null) {
    buffer.append(content)
  }
  val maybeJson = buffer.toString()
  logger.debug { "tryParseJson: maybeJson=$maybeJson" }

  // If the buffer is empty, there's nothing to parse
  if (maybeJson.isBlank()) {
    logger.debug { "tryParseJson: Buffer is empty, nothing to parse" }
    return null
  }

  // Find all potential JSON objects in the buffer
  val candidates = findJsonCandidates(maybeJson)
  logger.debug { "tryParseJson: Found ${candidates.size} JSON candidates" }

  // Try to parse each candidate
  candidates.forEachIndexed { index, candidate ->
    logger.debug { "tryParseJson: candidate[$index]=$candidate" }
    try {
      logger.debug { "tryParseJson: Attempting to parse candidate[$index]" }

      // Try to parse the candidate as a JSON object
      val element = schema.outputFromJson(candidate)
      logger.debug { "tryParseJson: Successfully parsed candidate[$index] to $element" }

      // If successful, call the onSuccess callback and clear the buffer
      val result = onSuccess(element)

      // Clear the buffer and log the action
      val bufferBefore = buffer.toString()
      buffer.clear()
      logger.debug { "tryParseJson: Buffer cleared after successful parse. Before: '$bufferBefore', After: '${buffer}'" }

      return result
    } catch (e: Exception) {
      logger.error(e) { "tryParseJson: Error parsing candidate[$index]: ${e.message}" }
      // Try to extract the error details for better debugging
      try {
        val errorDetails = e.toString()
        logger.debug { "tryParseJson: Error details: $errorDetails" }
      } catch (e2: Exception) {
        logger.error(e2) { "tryParseJson: Could not extract error details: ${e2.message}" }
      }
      // Ignore parsing errors and continue collecting chunks
    }
  }

  logger.debug { "tryParseJson: No valid JSON found, returning null" }
  return null
}

/**
 * Finds all potential JSON objects in a string
 */
private fun findJsonCandidates(text: String): List<String> {
  val candidates = mutableListOf<String>()

  // Find all opening braces
  val openingBraces = mutableListOf<Int>()
  for (i in text.indices) {
    if (text[i] == '{') {
      openingBraces.add(i)
    }
  }

  // For each opening brace, try to find a matching closing brace
  for (start in openingBraces) {
    var depth = 0
    for (i in start until text.length) {
      when (text[i]) {
        '{' -> depth++
        '}' -> {
          depth--
          if (depth == 0) {
            // Found a matching closing brace
            candidates.add(text.substring(start, i + 1))
            break
          }
        }
      }
    }
  }

  return candidates
}

/**
 * Checks if a string is valid JSON
 */
fun isValidJson(jsonString: String): Boolean {
  if (jsonString.isBlank()) return false

  // Check for basic JSON structure
  if (!jsonString.trim().startsWith("{") || !jsonString.trim().endsWith("}")) {
    return false
  }

  // Try to parse the JSON
  return try {
    KotlinSchema.json.parseToJsonElement(jsonString)
    true
  } catch (e: Exception) {
    false
  }
}
