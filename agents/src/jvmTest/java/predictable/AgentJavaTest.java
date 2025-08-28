package predictable;

import org.junit.Test;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import predictable.agent.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;
import static predictable.agent.Message.assistant;
import static predictable.agent.Message.user;

/**
 * Java interoperability tests for the Agent class.
 * Verifies that the Agent API is usable from pure Java code.
 */
public class AgentJavaTest {

    // Test data records
    record QueryInput(String query, int maxResults) {}

    record SearchResult(String title, String content, double score) {}

    private Agent createTestAgent() {
        return new Agent(
            "JavaTestAgent",
            "An agent for Java interop testing",
            "You are a helpful assistant for testing Java interoperability. Keep responses concise."
        );
    }

    @Test
    public void testBlockingTextGeneration() {
        var agent = createTestAgent();
        
        // Test blocking text generation
        var response = agent.text("What is 2+2?");
        
        assertNotNull("Response should not be null", response);
        assertFalse("Response should not be empty", response.isEmpty());
        System.out.println("Blocking response: " + response);
    }

    @Test
    public void testAsyncTextGeneration() throws Exception {
        var agent = createTestAgent();
        
        // Test async text generation
        var futureResponse = agent.textAsync(
            "What is the capital of France?"
        );
        
        String response = futureResponse.get(30, TimeUnit.SECONDS);
        
        assertNotNull("Response should not be null", response);
        assertFalse("Response should not be empty", response.isEmpty());
        System.out.println("Async response: " + response);
    }

    @Test
    public void testBlockingMessageHistory() {
        var agent = createTestAgent();
        
        // Create a conversation history
        var messages = Arrays.asList(
            user("Hello, who are you?"),
            assistant("I am a helpful AI assistant."),
            user("What can you help me with?")
        );
        
        // Test blocking message history processing
        var response = agent.text(messages);
        
        assertNotNull("Response should not be null", response);
        assertFalse("Response should not be empty", response.isEmpty());
        System.out.println("Message history response: " + response);
    }

    @Test
    public void testAsyncChatWithMetadata() throws Exception {
        var agent = createTestAgent();
        
        // Test async chat with metadata
        var futureResponse = agent.chatAsync(
            "Tell me a fun fact"
        );

        var response = futureResponse.get(30, TimeUnit.SECONDS);
        
        assertNotNull("Response should not be null", response);
        assertNotNull("Response value should not be null", response.getValue());
        
        // Check metadata
        assertNotNull("Metadata should not be null", response.getMetadata());
        System.out.println("Chat response: " + response.getValue());
        System.out.println("Metadata - Model: " + response.getMetadata().getModel());
        System.out.println("Metadata - Usage: prompt=" + response.getMetadata().getPromptTokens() +
                          ", completion=" + response.getMetadata().getCompletionTokens());
    }

    @Test
    public void testBlockingStructuredGeneration() {
        var agent = createTestAgent();
        
        // Test blocking structured generation
        var input = new QueryInput("artificial intelligence", 5);

        var result = agent.generateObjectBlocking(
            input,
            QueryInput.class,
            SearchResult.class
        );

        assertNotNull("Result should not be null", result);
        assertNotNull("Result title should not be null", result.title());
        assertNotNull("Result content should not be null", result.content());
        assertTrue("Result score should be positive", result.score() >= 0);
        
        System.out.println("Structured result: " + result.title() + " - " + result.content());
    }

    @Test
    public void testAsyncStructuredGeneration() throws Exception {
        var agent = createTestAgent();
        
        // Test async structured generation
        var input = new QueryInput("quantum computing", 3);

        var futureResult = agent.generateObjectAsync(
            input,
            QueryInput.class,
            SearchResult.class
        );

        var result = futureResult.get(30, TimeUnit.SECONDS);
        
        assertNotNull("Result should not be null", result);
        assertNotNull("Result title should not be null", result.title());
        assertNotNull("Result content should not be null", result.content());
        
        System.out.println("Async structured result: " + result.title());
    }

    @Test
    public void testPublisherStreaming() throws Exception {
        var agent = createTestAgent();
        
        // Test publisher-based streaming
        var publisher = agent.streamTextAsPublisher(
            "Count from 1 to 5"
        );

        var latch = new CountDownLatch(1);
        var chunkCount = new AtomicInteger(0);
        var fullResponse = new StringBuilder();
        
        publisher.subscribe(new Subscriber<>() {
            private Subscription subscription;

            @Override
            public void onSubscribe(Subscription s) {
                this.subscription = s;
                s.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(StreamResponse<String> response) {
                chunkCount.incrementAndGet();
                if (response instanceof StreamResponse.Chunk) {
                    StreamResponse.Chunk<String> chunk = (StreamResponse.Chunk<String>) response;
                    fullResponse.append(chunk.getValue());
                    System.out.print(chunk.getValue());
                }
            }

            @Override
            public void onError(Throwable t) {
                t.printStackTrace();
                latch.countDown();
            }

            @Override
            public void onComplete() {
                System.out.println("\nStreaming completed");
                latch.countDown();
            }
        });
        
        assertTrue("Streaming should complete within timeout", latch.await(30, TimeUnit.SECONDS));
        assertTrue("Should receive at least one chunk", chunkCount.get() > 0);
        assertFalse("Full response should not be empty", fullResponse.toString().isEmpty());
        
        System.out.println("Received " + chunkCount.get() + " chunks");
    }

    // TODO: Fix Elements schema generation for streaming structured responses
    // @Test
    public void testStructuredPublisherStreaming() throws Exception {
        var agent = createTestAgent();

        var input = new QueryInput("streaming test", 1);
        
        // Test structured publisher streaming
        var publisher = agent.streamObjectAsPublisher(
            input,
            QueryInput.class,
            SearchResult.class
        );

        var latch = new CountDownLatch(1);
        var chunkCount = new AtomicInteger(0);
        List<SearchResult> results = new ArrayList<>();
        
        publisher.subscribe(new Subscriber<>() {
            @Override
            public void onSubscribe(Subscription s) {
                s.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(StreamResponse<SearchResult> response) {
                chunkCount.incrementAndGet();
                if (response instanceof StreamResponse.Chunk<SearchResult> chunk) {
                    results.add(chunk.getValue());
                    System.out.println("Received structured chunk: " + chunk.getValue().title());
                }
            }

            @Override
            public void onError(Throwable t) {
                t.printStackTrace();
                latch.countDown();
            }

            @Override
            public void onComplete() {
                System.out.println("Structured streaming completed");
                latch.countDown();
            }
        });
        
        assertTrue("Streaming should complete within timeout", latch.await(30, TimeUnit.SECONDS));
        assertTrue("Should receive at least one chunk", chunkCount.get() > 0);
        
        System.out.println("Received " + chunkCount.get() + " structured chunks");
    }

    @Test
    public void testAgentInputVariants() {
        Agent agent = createTestAgent();
        
        // Test with AgentInput.Text using @JvmOverloads benefit
        AgentInput.Text textInput = new AgentInput.Text(
            "Hello from Java",
            new RequestParameters()  // Using @JvmOverloads - no need to specify all parameters
        );
        String textResponse = agent.text(textInput);
        assertNotNull("Text response should not be null", textResponse);
        
        // Test with AgentInput.Messages using @JvmOverloads benefit
        List<Message> messages = Arrays.asList(
            new Message(MessageRole.User.INSTANCE, "Test message from Java"),  // Using @JvmOverloads constructor
            new Message(MessageRole.Assistant.INSTANCE, "I understand"),  // No need for optional parameters
            new Message(MessageRole.User.INSTANCE, "Great!")
        );
        AgentInput.Messages messagesInput = new AgentInput.Messages(
            messages, 
            new RequestParameters()  // Using @JvmOverloads
        );
        String messagesResponse = agent.text(messagesInput);
        assertNotNull("Messages response should not be null", messagesResponse);
        
        System.out.println("AgentInput.Text response: " + textResponse);
        System.out.println("AgentInput.Messages response: " + messagesResponse);
    }

    @Test
    public void testMultipleAsyncRequests() throws Exception {
        var agent = createTestAgent();
        
        // Launch multiple async requests
        var future1 = agent.textAsync("What is 1+1?");
        var future2 = agent.textAsync("What is 2+2?");
        var future3 = agent.textAsync("What is 3+3?");

        // Verify all responses
        assertNotNull("Response 1 should not be null", future1.get());
        assertNotNull("Response 2 should not be null", future2.get());
        assertNotNull("Response 3 should not be null", future3.get());
        
        System.out.println("All async requests completed successfully");
    }

    @Test
    public void testJvmOverloadsRequestParameters() {
        Agent agent = createTestAgent();
        
        // Test creating RequestParameters with @JvmOverloads - various constructor overloads
        RequestParameters defaultParams = new RequestParameters();
        assertNotNull("Default parameters should not be null", defaultParams);
        
        // Test with only temperature
        RequestParameters tempParams = new RequestParameters(0.7);
        assertEquals(0.7, tempParams.getTemperature(), 0.001);
        
        // Test with temperature and topP
        RequestParameters tempAndTopParams = new RequestParameters(0.7, 0.9);
        assertEquals(0.7, tempAndTopParams.getTemperature(), 0.001);
        assertEquals(0.9, tempAndTopParams.getTopP(), 0.001);
        
        // Test with multiple parameters
        RequestParameters multiParams = new RequestParameters(
            0.7,    // temperature
            0.9,    // topP
            1,      // n
            null,   // stop
            false   // store
        );
        assertEquals(0.7, multiParams.getTemperature(), 0.001);
        assertEquals(0.9, multiParams.getTopP(), 0.001);
        assertEquals(Integer.valueOf(1), multiParams.getN());
        assertFalse(multiParams.getStore());
        
        System.out.println("RequestParameters @JvmOverloads test passed");
    }
    
    @Test
    public void testJvmOverloadsMessage() {
        // Test Message with @JvmOverloads - various constructor overloads
        
        // Simple message with just role and content
        Message simpleMsg = new Message(MessageRole.User.INSTANCE, "Hello");
        assertEquals(MessageRole.User.INSTANCE, simpleMsg.getRole());
        assertEquals("Hello", simpleMsg.getContent());
        assertNull(simpleMsg.getName());
        assertNull(simpleMsg.getToolCalls());
        
        // Message with role, content, and name
        Message namedMsg = new Message(MessageRole.User.INSTANCE, "Hello", "John");
        assertEquals("John", namedMsg.getName());
        
        // Message with role, content, name, and tool calls
        Message toolMsg = new Message(
            MessageRole.Assistant.INSTANCE, 
            "Using tool", 
            "Assistant",
            new ArrayList<>()  // Empty tool calls list
        );
        assertNotNull(toolMsg.getToolCalls());
        assertEquals(0, toolMsg.getToolCalls().size());
        
        System.out.println("Message @JvmOverloads test passed");
    }
    
    @Test
    public void testCombinedJvmOverloadsUsage() {
        // Test combining @JvmOverloads from multiple classes
        Agent agent = createTestAgent();
        
        // Create messages with minimal parameters
        List<Message> conversation = Arrays.asList(
            new Message(MessageRole.System.INSTANCE, "You are a helpful assistant"),
            new Message(MessageRole.User.INSTANCE, "What is Java?"),
            new Message(MessageRole.Assistant.INSTANCE, "Java is a programming language"),
            new Message(MessageRole.User.INSTANCE, "Tell me more")
        );
        
        // Create request parameters with just temperature
        RequestParameters params = new RequestParameters(0.5);
        
        // Create AgentInput with the simplified constructors
        AgentInput.Messages input = new AgentInput.Messages(conversation, params);
        
        // Test the agent
        String response = agent.text(input);
        assertNotNull("Response should not be null", response);
        
        System.out.println("Combined @JvmOverloads usage test passed");
    }
}
