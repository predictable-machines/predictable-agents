package predictable;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Test to verify Java records work correctly with the Agent API.
 */
public class JavaRecordTest {

    // Test records
    record UserQuery(String question, int maxLength) {}
    
    record Answer(String text, double confidence) {}
    
    record ComplexInput(
        String prompt, 
        int maxTokens, 
        double temperature,
        boolean streaming
    ) {}
    
    record ComplexOutput(
        String response,
        int tokensUsed,
        long processingTimeMs,
        String model
    ) {}

    @Test
    public void testRecordsCreation() {
        // Test simple record creation
        UserQuery query = new UserQuery("What is Java?", 100);
        assertEquals("What is Java?", query.question());
        assertEquals(100, query.maxLength());
        
        // Test record with double
        Answer answer = new Answer("Java is a programming language", 0.95);
        assertEquals("Java is a programming language", answer.text());
        assertEquals(0.95, answer.confidence(), 0.001);
        
        // Test complex record
        ComplexInput input = new ComplexInput("Generate code", 500, 0.7, true);
        assertEquals("Generate code", input.prompt());
        assertEquals(500, input.maxTokens());
        assertEquals(0.7, input.temperature(), 0.001);
        assertTrue(input.streaming());
    }

    @Test
    public void testRecordsEquality() {
        UserQuery query1 = new UserQuery("Test", 50);
        UserQuery query2 = new UserQuery("Test", 50);
        UserQuery query3 = new UserQuery("Test", 100);
        
        // Records with same values should be equal
        assertEquals(query1, query2);
        assertEquals(query1.hashCode(), query2.hashCode());
        
        // Records with different values should not be equal
        assertNotEquals(query1, query3);
    }

    @Test
    public void testRecordsToString() {
        UserQuery query = new UserQuery("Hello", 42);
        String str = query.toString();
        
        // toString should contain the record name and field values
        assertTrue(str.contains("UserQuery"));
        assertTrue(str.contains("Hello"));
        assertTrue(str.contains("42"));
    }

    @Test
    public void testRecordsWithAgent() {
        // Create test agent
        Agent agent = new Agent(
            "RecordTestAgent",
            "Agent for testing records",
            "You are a test assistant"
        );
        
        // Verify agent creation with records
        assertNotNull(agent);
        
        // Create records for use with agent
        UserQuery input = new UserQuery("Test question", 100);
        assertNotNull(input);
        
        // These would be used with agent methods like:
        // agent.generateObjectBlocking(input, UserQuery.class, Answer.class);
        // But we don't execute to avoid API calls
    }

    @Test
    public void testRecordSerialization() {
        // Test that records can be used as input/output types
        Class<UserQuery> inputClass = UserQuery.class;
        Class<Answer> outputClass = Answer.class;
        
        // Create schema objects (verifies records work with reflection)
        ClassInputSchema<UserQuery> inputSchema = new ClassInputSchema<>(inputClass);
        ClassOutputSchema<Answer> outputSchema = new ClassOutputSchema<>(outputClass);
        
        // Test serialization
        UserQuery query = new UserQuery("Serialize me", 200);
        String json = inputSchema.inputToJson(query);
        assertNotNull(json);
        assertTrue(json.contains("Serialize me"));
        assertTrue(json.contains("200"));
        
        // Test deserialization
        UserQuery deserialized = inputSchema.inputFromJson(json);
        assertEquals(query, deserialized);
    }

    @Test
    public void testRecordJsonSchema() {
        // Test JSON schema generation for records
        ClassOutputSchema<Answer> schema = new ClassOutputSchema<>(Answer.class);
        
        String jsonSchema = schema.outputJsonSchema();
        assertNotNull(jsonSchema);
        
        // Should contain type information
        assertTrue(jsonSchema.contains("\"type\"") || jsonSchema.contains("Answer"));
        
        System.out.println("Record JSON Schema: " + jsonSchema);
    }
}