package predictable.tool

sealed interface ToolChoice {

  data class Mode(public val value: String) : ToolChoice


  data class Named(
    val type: ToolType? = null,
    val function: FunctionToolChoice? = null,
  ) : ToolChoice

  companion object {
    /** Represents the `auto` mode. */
    val Auto: ToolChoice = Mode("auto")

    /** Represents the `none` mode. */
    val None: ToolChoice = Mode("none")

    /** Specifies a function for the model to call **/
    fun function(name: String): ToolChoice =
      Named(type = ToolType.Function, function = FunctionToolChoice(name = name))
  }
}
