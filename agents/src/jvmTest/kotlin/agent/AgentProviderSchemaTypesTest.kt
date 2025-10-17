package predictable.agent

import com.xemantic.ai.tool.schema.meta.Description
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Required
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import predictable.Agent
import predictable.TestUtils.workflowWithEmptyState
import predictable.Tool
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Comprehensive test suite for validating JSON Schema to Koog ToolParameterType mappings.
 *
 * Tests all supported schema types:
 * - Primitives: String, Integer, Float, Boolean
 * - Enums
 * - Arrays (of primitives and objects)
 * - Complex Objects (nested, with arrays)
 * - Mixed nested structures
 */
class AgentProviderSchemaTypesTest {

    // ==================== Primitive Types ====================

    @Serializable
    @SerialName("PrimitiveTypesInput")
    @Description("Input with all primitive types")
    data class PrimitiveTypesInput(
        @Required val stringValue: String,
        @Required val integerValue: Int,
        @Required val floatValue: Float,
        @Required val booleanValue: Boolean
    )

    @Serializable
    @SerialName("PrimitiveTypesOutput")
    data class PrimitiveTypesOutput(
        @Required val message: String
    )

    @Test
    fun `test tool with all primitive types`() = workflowWithEmptyState {
        val tool = Tool<PrimitiveTypesInput, PrimitiveTypesOutput> { input ->
            PrimitiveTypesOutput("Received: ${input.stringValue}, ${input.integerValue}, ${input.floatValue}, ${input.booleanValue}")
        }

        // Verify schema generation works
        val jsonSchema = tool.schema.inputJsonSchema()
        assertNotNull(jsonSchema)
        assertTrue(jsonSchema.contains("string"))
        assertTrue(jsonSchema.contains("integer") || jsonSchema.contains("number"))
        assertTrue(jsonSchema.contains("boolean"))

        // Test tool execution
        val input = PrimitiveTypesInput("test", 42, 3.14f, true)
        val output = tool.invoke(input)
        assertEquals("Received: test, 42, 3.14, true", output.message)
    }

    // ==================== Enum Types ====================

    enum class Priority { LOW, MEDIUM, HIGH, CRITICAL }

    @Serializable
    @SerialName("EnumInput")
    data class EnumInput(
        @Required val priority: Priority,
        @Required val task: String
    )

    @Serializable
    @SerialName("EnumOutput")
    data class EnumOutput(
        @Required val result: String
    )

    @Test
    fun `test tool with enum type`() = workflowWithEmptyState {
        val tool = Tool<EnumInput, EnumOutput> { input ->
            EnumOutput("Task: ${input.task} with priority ${input.priority}")
        }

        val jsonSchema = tool.schema.inputJsonSchema()
        assertNotNull(jsonSchema)

        val input = EnumInput(Priority.HIGH, "Fix bug")
        val output = tool.invoke(input)
        assertEquals("Task: Fix bug with priority HIGH", output.result)
    }

    // ==================== Array Types ====================

    @Serializable
    @SerialName("ArrayOfPrimitivesInput")
    data class ArrayOfPrimitivesInput(
        @Required val tags: List<String>,
        @Required val scores: List<Int>,
        @Required val weights: List<Float>
    )

    @Serializable
    @SerialName("ArrayOfPrimitivesOutput")
    data class ArrayOfPrimitivesOutput(
        @Required val summary: String
    )

    @Test
    fun `test tool with arrays of primitives`() = workflowWithEmptyState {
        val tool = Tool<ArrayOfPrimitivesInput, ArrayOfPrimitivesOutput> { input ->
            ArrayOfPrimitivesOutput("Tags: ${input.tags.size}, Scores: ${input.scores.sum()}, Avg Weight: ${input.weights.average()}")
        }

        val jsonSchema = tool.schema.inputJsonSchema()
        assertNotNull(jsonSchema)
        assertTrue(jsonSchema.contains("array") || jsonSchema.contains("list"))

        val input = ArrayOfPrimitivesInput(
            tags = listOf("kotlin", "testing"),
            scores = listOf(10, 20, 30),
            weights = listOf(0.5f, 1.5f, 2.0f)
        )
        val output = tool.invoke(input)
        assertTrue(output.summary.contains("Tags: 2"))
        assertTrue(output.summary.contains("Scores: 60"))
    }

    // ==================== Nested Object Types ====================

    @Serializable
    @SerialName("Address")
    data class Address(
        @Required val street: String,
        @Required val city: String,
        @Required val zipCode: String
    )

    @Serializable
    @SerialName("Person")
    data class Person(
        @Required val name: String,
        @Required val age: Int,
        @Required val address: Address
    )

    @Serializable
    @SerialName("NestedObjectInput")
    data class NestedObjectInput(
        @Required val person: Person
    )

    @Serializable
    @SerialName("NestedObjectOutput")
    data class NestedObjectOutput(
        @Required val info: String
    )

    @Test
    fun `test tool with nested objects`() = workflowWithEmptyState {
        val tool = Tool<NestedObjectInput, NestedObjectOutput> { input ->
            NestedObjectOutput("${input.person.name} lives in ${input.person.address.city}")
        }

        val jsonSchema = tool.schema.inputJsonSchema()
        assertNotNull(jsonSchema)
        assertTrue(jsonSchema.contains("properties") || jsonSchema.contains("object"))

        val input = NestedObjectInput(
            Person(
                name = "Alice",
                age = 30,
                address = Address("123 Main St", "Springfield", "12345")
            )
        )
        val output = tool.invoke(input)
        assertEquals("Alice lives in Springfield", output.info)
    }

    // ==================== Arrays of Objects ====================

    @Serializable
    @SerialName("Task")
    data class Task(
        @Required val title: String,
        @Required val priority: Int,
        @Required val completed: Boolean
    )

    @Serializable
    @SerialName("ArrayOfObjectsInput")
    data class ArrayOfObjectsInput(
        @Required val tasks: List<Task>
    )

    @Serializable
    @SerialName("ArrayOfObjectsOutput")
    data class ArrayOfObjectsOutput(
        @Required val summary: String
    )

    @Test
    fun `test tool with arrays of objects`() = workflowWithEmptyState {
        val tool = Tool<ArrayOfObjectsInput, ArrayOfObjectsOutput> { input ->
            val completed = input.tasks.count { it.completed }
            ArrayOfObjectsOutput("${completed}/${input.tasks.size} tasks completed")
        }

        val jsonSchema = tool.schema.inputJsonSchema()
        assertNotNull(jsonSchema)

        val input = ArrayOfObjectsInput(
            listOf(
                Task("Task 1", 1, true),
                Task("Task 2", 2, false),
                Task("Task 3", 3, true)
            )
        )
        val output = tool.invoke(input)
        assertEquals("2/3 tasks completed", output.summary)
    }

    // ==================== Complex Mixed Structures ====================

    @Serializable
    @SerialName("Contact")
    data class Contact(
        @Required val email: String,
        @Required val phones: List<String>
    )

    @Serializable
    @SerialName("Project")
    data class Project(
        @Required val name: String,
        @Required val members: List<String>,
        @Required val metadata: Map<String, String>
    )

    @Serializable
    @SerialName("ComplexInput")
    data class ComplexInput(
        @Required val user: Person,
        @Required val contacts: List<Contact>,
        @Required val projects: List<Project>,
        @Required val preferences: Map<String, Boolean>
    )

    @Serializable
    @SerialName("ComplexOutput")
    data class ComplexOutput(
        @Required val analysis: String
    )

    @Test
    fun `test tool with complex mixed structures`() = workflowWithEmptyState {
        val tool = Tool<ComplexInput, ComplexOutput> { input ->
            val analysis = buildString {
                append("User: ${input.user.name}, ")
                append("Contacts: ${input.contacts.size}, ")
                append("Projects: ${input.projects.size}, ")
                append("Preferences: ${input.preferences.size}")
            }
            ComplexOutput(analysis)
        }

        val jsonSchema = tool.schema.inputJsonSchema()
        assertNotNull(jsonSchema)

        val input = ComplexInput(
            user = Person("Bob", 35, Address("456 Elm St", "Portland", "67890")),
            contacts = listOf(
                Contact("bob@example.com", listOf("555-1234", "555-5678"))
            ),
            projects = listOf(
                Project("Project A", listOf("Alice", "Bob"), mapOf("status" to "active"))
            ),
            preferences = mapOf("darkMode" to true, "notifications" to false)
        )
        val output = tool.invoke(input)
        assertTrue(output.analysis.contains("User: Bob"))
        assertTrue(output.analysis.contains("Contacts: 1"))
        assertTrue(output.analysis.contains("Projects: 1"))
    }

    // ==================== Agent Integration Tests ====================

    @Test
    fun `test agent with complex tool schema`() = workflowWithEmptyState {
        val testAgent = Agent(
            name = "Complex Schema Test Agent",
            description = "Agent for testing complex schema types",
            system = "You are a helpful assistant that processes complex data structures.",
            model = Model.defaultModel,
            tools = listOf(
                Tool<ArrayOfObjectsInput, ArrayOfObjectsOutput> { input ->
                    val completed = input.tasks.count { it.completed }
                    ArrayOfObjectsOutput("${completed}/${input.tasks.size} completed")
                }
            )
        )

        assertNotNull(testAgent)
        assertEquals(1, testAgent.tools.size)

        // Verify the tool schema can be generated
        val tool = testAgent.tools.first()
        val schema = tool.schema.inputJsonSchema()
        assertNotNull(schema)
    }

    // ==================== Optional Fields Test ====================

    @Serializable
    @SerialName("OptionalFieldsInput")
    data class OptionalFieldsInput(
        @Required val requiredField: String,
        val optionalString: String? = null,
        val optionalInt: Int? = null,
        val optionalList: List<String>? = null
    )

    @Serializable
    @SerialName("OptionalFieldsOutput")
    data class OptionalFieldsOutput(
        @Required val result: String
    )

    @Test
    fun `test tool with optional fields`() = workflowWithEmptyState {
        val tool = Tool<OptionalFieldsInput, OptionalFieldsOutput> { input ->
            val parts = mutableListOf(input.requiredField)
            input.optionalString?.let { parts.add(it) }
            input.optionalInt?.let { parts.add(it.toString()) }
            input.optionalList?.let { parts.add(it.joinToString(",")) }
            OptionalFieldsOutput(parts.joinToString(" | "))
        }

        val jsonSchema = tool.schema.inputJsonSchema()
        assertNotNull(jsonSchema)

        // Test with only required field
        val input1 = OptionalFieldsInput(requiredField = "required")
        val output1 = tool.invoke(input1)
        assertEquals("required", output1.result)

        // Test with all fields
        val input2 = OptionalFieldsInput(
            requiredField = "required",
            optionalString = "optional",
            optionalInt = 42,
            optionalList = listOf("a", "b", "c")
        )
        val output2 = tool.invoke(input2)
        assertEquals("required | optional | 42 | a,b,c", output2.result)
    }

    // ==================== Edge Cases ====================

    @Serializable
    @SerialName("EmptyObjectInput")
    data class EmptyObjectInput(
        @Required val id: String
    )

    @Serializable
    @SerialName("EmptyListInput")
    data class EmptyListInput(
        @Required val items: List<String>
    )

    @Serializable
    @SerialName("EdgeCaseOutput")
    data class EdgeCaseOutput(
        @Required val result: String
    )

    @Test
    fun `test tool with empty collections`() = workflowWithEmptyState {
        val emptyListTool = Tool<EmptyListInput, EdgeCaseOutput> { input ->
            EdgeCaseOutput("Items count: ${input.items.size}")
        }

        val input = EmptyListInput(emptyList())
        val output = emptyListTool.invoke(input)
        assertEquals("Items count: 0", output.result)
    }

    @Test
    fun `test tool schema generation for all types`() = workflowWithEmptyState {
        // Create tools with different schema types
        val tools = listOf(
            Tool<PrimitiveTypesInput, PrimitiveTypesOutput> { input ->
                PrimitiveTypesOutput("primitives")
            },
            Tool<ArrayOfPrimitivesInput, ArrayOfPrimitivesOutput> { input ->
                ArrayOfPrimitivesOutput("arrays")
            },
            Tool<NestedObjectInput, NestedObjectOutput> { input ->
                NestedObjectOutput("nested")
            },
            Tool<ComplexInput, ComplexOutput> { input ->
                ComplexOutput("complex")
            }
        )

        // Verify all schemas can be generated
        tools.forEach { tool ->
            val schema = tool.schema.inputJsonSchema()
            assertNotNull(schema, "Schema should not be null for tool: ${tool.name}")
            assertTrue(schema.isNotEmpty(), "Schema should not be empty for tool: ${tool.name}")
        }
    }
}
