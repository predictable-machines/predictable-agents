package predictable.agent

import predictable.tool.InputSchema

/**
 * Represents different types of input that can be provided to an Agent.
 * 
 * This sealed interface provides type-safe abstractions for various input formats
 * that agents can process, including structured data, plain text, and conversation history.
 * Each input type carries its own request parameters for customizing the agent's behavior.
 */
sealed interface AgentInput {

  /**
   * Request parameters that configure how the agent processes this input.
   * Controls aspects like temperature, max tokens, and other model-specific settings.
   */
  val requestParameters: RequestParameters

  /**
   * Input containing structured data with a defined schema.
   * 
   * Used when the agent needs to process typed, validated input data.
   * The schema ensures proper serialization and validation of the input.
   * 
   * @param T The type of the structured input value
   * @property value The actual input data of type [T]
   * @property schema Schema for validating and serializing the input
   * @property requestParameters Parameters for configuring the agent's processing
   */
  data class Structured<T>(
    val value: T,
    val schema: InputSchema<T>,
    override val requestParameters: RequestParameters
  ) : AgentInput


  /**
   * Simple text input for the agent.
   * 
   * Used for basic text prompts and queries that don't require structure or history.
   * 
   * @property value The text prompt to process
   * @property requestParameters Parameters for configuring the agent's processing
   */
  data class Text(val value: String, override val requestParameters: RequestParameters) : AgentInput


  /**
   * Conversation history input for the agent.
   * 
   * Used when the agent needs to process a full conversation context,
   * including previous messages from users, assistants, and system prompts.
   * 
   * @property value List of messages representing the conversation history
   * @property requestParameters Parameters for configuring the agent's processing
   */
  data class Messages(val value: List<Message>, override val requestParameters: RequestParameters) : AgentInput

}
