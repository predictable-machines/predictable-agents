package predictable.tool

/**
 * Represents the type of tool available to the AI.
 * 
 * Used to categorize different kinds of tools that can be invoked
 * during AI processing. Currently supports function-type tools.
 * 
 * @property value The string identifier for the tool type
 */
data class ToolType(val value: String) {
  companion object {
    /**
     * Function tool type.
     * Represents tools that are callable functions with input/output.
     */
    val Function: ToolType = ToolType("function")
  }
}
