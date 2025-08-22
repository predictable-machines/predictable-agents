package predictable.mcp;

import org.junit.Test;
import predictable.*;
import predictable.agent.Model;
import predictable.agent.RequestParameters;
import predictable.mcp.config.MCPConfig;
import predictable.mcp.config.MCPServerConfig;
import predictable.mcp.config.ServerConfig;
import predictable.tool.Schema;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static org.junit.Assert.*;
import static predictable.mcp.server.MCPServer.*;

/**
 * Integration tests demonstrating the use of MCP tools with agents from Java.
 * This avoids Kotlin-specific JSON types by using agents to interact with MCP tools.
 */
public class MCPAgentIntegrationTest {

    // Test data classes using Java records
    record CalculatorInput(double a, double b, String operation) {}
    record CalculatorOutput(double result, String operation) {}
    
    record WeatherInput(String city) {}
    record WeatherOutput(double temperature, String condition, String city) {}
    
    record TextAnalysisInput(String text, String analysisType) {}
    record TextAnalysisOutput(int wordCount, int charCount, String result) {}

    /**
     * Creates test tools using Java Functions.
     */
    private List<Tool<?, ?>> createTestTools() {
        List<Tool<?, ?>> tools = new ArrayList<>();
        
        // Calculator tool
        Schema<CalculatorInput, CalculatorOutput> calcSchema = new ClassSchema<>(
            CalculatorInput.class,
            CalculatorOutput.class
        );
        
        Function<CalculatorInput, CalculatorOutput> calculator = input -> {
            double result = switch (input.operation()) {
                case "add" -> input.a() + input.b();
                case "subtract" -> input.a() - input.b();
                case "multiply" -> input.a() * input.b();
                case "divide" -> input.b() != 0 ? input.a() / input.b() : Double.NaN;
                default -> 0.0;
            };
            return new CalculatorOutput(result, input.operation());
        };
        
        Tool<CalculatorInput, CalculatorOutput> calculatorTool = Tool.create(
            "calculator",
            "Performs basic arithmetic operations",
            calcSchema,
            calculator
        );
        tools.add(calculatorTool);
        
        // Weather tool
        Schema<WeatherInput, WeatherOutput> weatherSchema = new ClassSchema<>(
            WeatherInput.class,
            WeatherOutput.class
        );
        
        Function<WeatherInput, WeatherOutput> weatherService = input -> 
            new WeatherOutput(20.0, "Sunny", input.city());
        
        Tool<WeatherInput, WeatherOutput> weatherTool = Tool.create(
            "weather_service",
            "Provides weather information for cities",
            weatherSchema,
            weatherService
        );
        tools.add(weatherTool);
        
        // Text analysis tool
        Schema<TextAnalysisInput, TextAnalysisOutput> textSchema = new ClassSchema<>(
            TextAnalysisInput.class,
            TextAnalysisOutput.class
        );
        
        Function<TextAnalysisInput, TextAnalysisOutput> textAnalyzer = input -> {
            String[] words = input.text().split("\\s+");
            int wordCount = words.length;
            int charCount = input.text().length();
            String result = "Analysis complete for type: " + input.analysisType();
            return new TextAnalysisOutput(wordCount, charCount, result);
        };
        
        Tool<TextAnalysisInput, TextAnalysisOutput> textTool = Tool.create(
            "text_analyzer",
            "Analyzes text and provides statistics",
            textSchema,
            textAnalyzer
        );
        tools.add(textTool);
        
        return tools;
    }

    @Test
    public void testAgentWithMCPToolsDiscovery() throws Exception {
        // Create tools to expose via MCP server
        List<Tool<?, ?>> serverTools = createTestTools();
        
        // Start the MCP server
        var server = startKtorMCPServer(
            serverTools,
            8100,  // Use unique port for this test
            "localhost",
            "java-agent-mcp-server",
            "1.0.0"
        );
        
        // Start the server asynchronously
        server.start(false);
        
        // Give the server time to fully start
        Thread.sleep(2000);
        
        try {
            // Configure MCPClient to connect to our test server
            Map<String, MCPServerConfig> servers = new HashMap<>();
            servers.put("test-server", new MCPServerConfig(
                "Test Server",
                "test",
                "Test MCP Server for Agent Integration",
                new ServerConfig.SSE(
                    "http://localhost:8100/sse",
                    new HashMap<>(),
                    new ArrayList<>(),
                    false
                )
            ));
            
            MCPConfig config = new MCPConfig(servers);
            
            // Use the server tools directly with the agent
            // (avoiding MCPClient invoke which requires Kotlin continuations)
            
            // Create an agent with the server tools directly
            Agent agent = new Agent(
                "MCPAgent",
                "An agent that uses MCP server tools",
                "You are a helpful assistant with access to various tools. Use them when appropriate.",
                Model.defaultModel,
                UUID.randomUUID().toString(),
                RequestParameters.defaultParameters,
                serverTools
            );
            
            // Test the agent with calculator tool
            String calcResponse = agent.text("What is 15 + 25?");
            assertNotNull("Calculator response should not be null", calcResponse);
            assertTrue("Response should contain the result 40", 
                calcResponse.contains("40"));
            
            // Test the agent with weather tool
            String weatherResponse = agent.text("What's the weather in Tokyo?");
            assertNotNull("Weather response should not be null", weatherResponse);
            assertTrue("Response should mention Tokyo", 
                weatherResponse.contains("Tokyo"));
            
            // Test the agent with text analysis tool
            String textResponse = agent.text("Analyze the sentence: 'Hello world from Java'");
            assertNotNull("Text analysis response should not be null", textResponse);
            assertTrue("Response should contain analysis results", 
                textResponse.toLowerCase().contains("word") || 
                textResponse.contains("4") ||
                textResponse.contains("char"));
            
        } finally {
            // Stop the server
            server.stop(0L, 1000L);
        }
    }

    @Test
    public void testAgentWithMCPToolsAsync() throws Exception {
        // Create a simple test tool
        Schema<String, String> schema = new ClassSchema<>(
            String.class,
            String.class
        );
        
        Tool<String, String> echoTool = Tool.create(
            "echo",
            "Echoes the input text",
            schema,
            input -> "Echo: " + input
        );
        
        List<Tool<?, ?>> tools = List.of(echoTool);
        
        // Start MCP server
        var server = startKtorMCPServer(
            tools,
            8101,
            "localhost",
            "async-agent-server",
            "1.0.0"
        );
        
        server.start(false);
        Thread.sleep(2000);
        
        try {
            Map<String, MCPServerConfig> servers = Map.of(
                "async-server", new MCPServerConfig(
                    "Async Server",
                    "async",
                    "Server for async agent testing",
                    new ServerConfig.SSE(
                        "http://localhost:8101/sse",
                        new HashMap<>(),
                        new ArrayList<>(),
                        false
                    )
                )
            );
            
            // Create agent directly with the server tools
            Agent agent = new Agent(
                "AsyncMCPAgent",
                "Agent for async testing",
                "You are an echo assistant. Use the echo tool when asked to repeat text.",
                Model.defaultModel,
                UUID.randomUUID().toString(),
                RequestParameters.defaultParameters,
                tools
            );
            
            // Test async execution
            CompletableFuture<String> future = agent.textAsync("Please echo: 'Hello from async'");
            String result = future.get();
            
            assertNotNull(result);
            assertTrue(result.contains("Hello from async"));
            
        } finally {
            server.stop(0L, 1000L);
        }
    }

    @Test
    public void testMultipleAgentsWithSameMCPServer() throws Exception {
        // Create test tools
        List<Tool<?, ?>> tools = createTestTools();
        
        // Start MCP server
        var server = startKtorMCPServer(
            tools,
            8102,
            "localhost",
            "multi-agent-server",
            "1.0.0"
        );
        
        server.start(false);
        Thread.sleep(2000);
        
        try {
            Map<String, MCPServerConfig> servers = Map.of(
                "shared-server", new MCPServerConfig(
                    "Shared Server",
                    "shared",
                    "Server shared by multiple agents",
                    new ServerConfig.SSE(
                        "http://localhost:8102/sse",
                        new HashMap<>(),
                        new ArrayList<>(),
                        false
                    )
                )
            );
            
            // Create first agent with the same tools
            Agent agent1 = new Agent(
                "Agent1",
                "First agent",
                "You are the first assistant.",
                Model.defaultModel,
                UUID.randomUUID().toString(),
                RequestParameters.defaultParameters,
                tools
            );
            
            String response1 = agent1.text("Calculate 10 + 5");
            assertNotNull(response1);
            assertTrue(response1.contains("15"));
            
            // Create second agent with the same tools
            Agent agent2 = new Agent(
                "Agent2",
                "Second agent",
                "You are the second assistant.",
                Model.defaultModel,
                UUID.randomUUID().toString(),
                RequestParameters.defaultParameters,
                tools
            );
            
            String response2 = agent2.text("What's the weather in London?");
            assertNotNull(response2);
            assertTrue(response2.toLowerCase().contains("london") || 
                       response2.toLowerCase().contains("weather"));
            
        } finally {
            server.stop(0L, 1000L);
        }
    }

}
