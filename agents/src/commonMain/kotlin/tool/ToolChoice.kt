package predictable.tool

/**
 * Represents the strategy for tool selection during AI processing.
 * 
 * Controls how the AI model decides whether and which tools to use
 * when generating responses. Can be automatic, disabled, or forced
 * to use a specific tool.
 */
sealed interface ToolChoice {

  /**
   * Represents a tool selection mode.
   * 
   * Standard modes like "auto" and "none" that control
   * the AI's tool usage behavior.
   * 
   * @property value The mode identifier (e.g., "auto", "none")
   */
  data class Mode(val value: String) : ToolChoice


  /**
   * Represents a specific named tool choice.
   * 
   * Forces the AI to use a particular tool by name,
   * overriding automatic tool selection.
   * 
   * @property type The type of tool (e.g., Function)
   * @property function The specific function tool to use
   */
  data class Named(
    val type: ToolType? = null,
    val function: FunctionToolChoice? = null,
  ) : ToolChoice

  companion object {
    /**
     * Automatic tool selection mode.
     * The AI decides whether and which tools to use based on the context.
     */
    val Auto: ToolChoice = Mode("auto")

    /**
     * Disables all tool usage.
     * The AI will not use any tools even if they're available.
     */
    val None: ToolChoice = Mode("none")

    /**
     * Creates a ToolChoice that forces the use of a specific function.
     * 
     * @param name The name of the function tool to use
     * @return A ToolChoice configured to use the specified function
     */
    fun function(name: String): ToolChoice =
      Named(type = ToolType.Function, function = FunctionToolChoice(name = name))
  }
}
