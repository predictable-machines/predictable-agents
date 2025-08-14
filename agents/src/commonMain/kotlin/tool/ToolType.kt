package predictable.tool

data class ToolType(val value: String) {
  companion object {
    val Function: ToolType = ToolType("function")
  }
}
