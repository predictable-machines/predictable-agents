package predictable.tool

/**
 * Represents a specific function tool choice for AI operations.
 * 
 * Used to explicitly specify which tool/function the AI should use
 * when multiple tools are available. This forces the AI to use
 * a particular tool rather than letting it choose automatically.
 * 
 * @property name The name identifier of the tool to use
 */
data class FunctionToolChoice(val name: String)
