package predictable

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class ClassSchemaTest {

    data class TestInput(
        val query: String,
        val maxResults: Int
    )

    data class TestOutput(
        val result: String,
        val score: Double
    )

    @Test
    fun `test ClassInputSchema generates proper JSON schema`() {
        val schema = ClassInputSchema(TestInput::class.java)
        
        val jsonSchema = schema.inputJsonSchema()
        assertNotNull(jsonSchema)
        assertTrue(jsonSchema.isNotEmpty())
        
        // Should contain type object or properties
        assertTrue(
            jsonSchema.contains("\"type\"") || jsonSchema.contains("\"properties\""),
            "Schema should contain type or properties: $jsonSchema"
        )
        
        println("Input JSON Schema: $jsonSchema")
    }

    @Test
    fun `test ClassOutputSchema generates proper JSON schema`() {
        val schema = ClassOutputSchema(TestOutput::class.java)
        
        val jsonSchema = schema.outputJsonSchema()
        assertNotNull(jsonSchema)
        assertTrue(jsonSchema.isNotEmpty())
        
        // Should contain type object or properties
        assertTrue(
            jsonSchema.contains("\"type\"") || jsonSchema.contains("\"properties\""),
            "Schema should contain type or properties: $jsonSchema"
        )
        
        println("Output JSON Schema: $jsonSchema")
    }

    @Test
    fun `test ClassSchema delegates properly`() {
        val schema = ClassSchema(TestInput::class.java, TestOutput::class.java)
        
        // Test input delegation
        val inputSchema = schema.inputJsonSchema()
        assertNotNull(inputSchema)
        assertEquals("TestInput", schema.inputSerialName())
        
        // Test output delegation
        val outputSchema = schema.outputJsonSchema()
        assertNotNull(outputSchema)
        assertEquals("TestOutput", schema.outputSerialName())
        
        println("Combined Schema - Input: $inputSchema")
        println("Combined Schema - Output: $outputSchema")
    }

    @Test
    fun `test ClassInputSchema serialization and deserialization`() {
        val schema = ClassInputSchema(TestInput::class.java)
        val input = TestInput("test query", 10)
        
        // Test serialization
        val json = schema.inputToJson(input)
        assertNotNull(json)
        assertTrue(json.contains("test query"))
        assertTrue(json.contains("10"))
        
        // Test deserialization
        val deserialized = schema.inputFromJson(json)
        assertEquals(input.query, deserialized.query)
        assertEquals(input.maxResults, deserialized.maxResults)
    }

    @Test
    fun `test ClassOutputSchema serialization and deserialization`() {
        val schema = ClassOutputSchema(TestOutput::class.java)
        val output = TestOutput("test result", 0.95)
        
        // Test serialization
        val json = schema.outputToJson(output)
        assertNotNull(json)
        assertTrue(json.contains("test result"))
        assertTrue(json.contains("0.95"))
        
        // Test deserialization
        val deserialized = schema.outputFromJson(json)
        assertEquals(output.result, deserialized.result)
        assertEquals(output.score, deserialized.score, 0.001)
    }
}