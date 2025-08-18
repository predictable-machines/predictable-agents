package predictable.mcp.resources

/**
 * Represents a resource exposed by an MCP server.
 * 
 * Resources in MCP are content items that can be read by clients. They represent
 * static or dynamic content that the server makes available, such as files,
 * documents, or computed data.
 * 
 * ## Resource Types
 * 
 * Resources can represent various types of content:
 * - **Files**: Local or remote files accessible by the server
 * - **APIs**: Data fetched from external APIs
 * - **Computed**: Dynamically generated content
 * - **Databases**: Query results or table contents
 * 
 * @property uri The unique resource identifier (URI) for accessing this resource
 * @property name Human-readable name for the resource
 * @property description Optional description of the resource's content or purpose
 * @property mimeType Optional MIME type indicating the resource's content format
 *                    (e.g., "text/plain", "application/json", "text/html")
 */
data class MCPResource(
  val uri: String,
  val name: String,
  val description: String?,
  val mimeType: String?,
)
