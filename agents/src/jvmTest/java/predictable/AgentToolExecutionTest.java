package predictable;

import org.junit.Test;
import predictable.agent.Model;
import predictable.agent.RequestParameters;
import kotlin.Unit;
import java.util.UUID;
import predictable.tool.Schema;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static org.junit.Assert.*;

/**
 * Tests for Java-created tools being executed by agents.
 * Verifies that tools created from Java Functions and static methods
 * work correctly when passed to and invoked by agents.
 * 
 * NOTE: OpenAI's function calling API requires that tool inputs must be
 * JSON objects, not primitive types. All tools must use record/class types
 * for inputs, even for simple values.
 */
public class AgentToolExecutionTest {

    // Test data classes
    record CalculatorInput(double a, double b, String operation) {}
    record CalculatorOutput(double result, String operation, String expression) {}
    
    record WeatherInput(String city) {}
    record WeatherOutput(double temperature, String description, String unit) {}
    
    record TextAnalysisInput(String text) {}
    record TextAnalysisOutput(int wordCount, int charCount, int sentenceCount) {}

    // Static utility methods to use as tools
    static class CalculatorUtils {
        public static CalculatorOutput calculate(CalculatorInput input) {
            double result = switch (input.operation().toLowerCase()) {
                case "add", "+" -> input.a() + input.b();
                case "subtract", "-" -> input.a() - input.b();
                case "multiply", "*" -> input.a() * input.b();
                case "divide", "/" -> input.b() != 0 ? input.a() / input.b() : Double.NaN;
                case "power", "^" -> Math.pow(input.a(), input.b());
                default -> Double.NaN;
            };
            
            String expression = String.format("%.2f %s %.2f = %.2f", 
                input.a(), input.operation(), input.b(), result);
            
            return new CalculatorOutput(result, input.operation(), expression);
        }
    }
    
    static class WeatherService {
        public static WeatherOutput getWeather(WeatherInput input) {
            // Simulate weather data based on city
            return switch (input.city().toLowerCase()) {
                case "san francisco" -> new WeatherOutput(65.0, "Foggy", "F");
                case "new york" -> new WeatherOutput(75.0, "Sunny", "F");
                case "london" -> new WeatherOutput(15.0, "Rainy", "C");
                case "tokyo" -> new WeatherOutput(25.0, "Clear", "C");
                default -> new WeatherOutput(70.0, "Unknown", "F");
            };
        }
    }
    
    static class TextAnalyzer {
        public static TextAnalysisOutput analyze(TextAnalysisInput input) {
            String text = input.text();
            if (text == null || text.isEmpty()) {
                return new TextAnalysisOutput(0, 0, 0);
            }
            
            String trimmed = text.trim();
            int wordCount = trimmed.isEmpty() ? 0 : trimmed.split("\\s+").length;
            int charCount = text.length();
            int sentenceCount = text.split("[.!?]+").length;
            
            return new TextAnalysisOutput(wordCount, charCount, sentenceCount);
        }
    }

    @Test
    public void testAgentExecutesJavaCalculatorTool() throws Exception {
        // Create calculator tool from static method
        Schema<CalculatorInput, CalculatorOutput> schema = new ClassSchema<>(
            CalculatorInput.class,
            CalculatorOutput.class
        );
        
        Tool<CalculatorInput, CalculatorOutput> calculatorTool = Tool.create(
            "calculator",
            "Performs mathematical calculations",
            schema,
            CalculatorUtils::calculate
        );
        
        // Create agent with the calculator tool
        Agent agent = new Agent(
            "MathAgent",
            "An agent that can perform calculations",
            "You are a helpful math assistant. Use the calculator tool to solve math problems.",
            Model.defaultModel,
            UUID.randomUUID().toString(),
            RequestParameters.defaultParameters,
            List.of(calculatorTool)
        );
        
        // Test the agent with a calculation request
        String response = agent.text("What is 25 + 17?");
        
        assertNotNull(response);
        assertTrue(response.contains("42"));
        
        // Test with more complex calculation
        String response2 = agent.text("Calculate 2 to the power of 8");
        assertTrue(response2.contains("256"));
    }

    @Test
    public void testAgentExecutesMultipleJavaTools() throws Exception {
        // Create weather tool
        Schema<WeatherInput, WeatherOutput> weatherSchema = new ClassSchema<>(
            WeatherInput.class,
            WeatherOutput.class
        );
        
        Tool<WeatherInput, WeatherOutput> weatherTool = Tool.create(
            "get_weather",
            "Gets current weather for a city",
            weatherSchema,
            WeatherService::getWeather
        );
        
        // Create text analysis tool
        Schema<TextAnalysisInput, TextAnalysisOutput> textSchema = new ClassSchema<>(
            TextAnalysisInput.class,
            TextAnalysisOutput.class
        );
        
        Tool<TextAnalysisInput, TextAnalysisOutput> textTool = Tool.create(
            "analyze_text",
            "Analyzes text for word count and other metrics",
            textSchema,
            TextAnalyzer::analyze
        );
        
        // Create agent with multiple tools
        Agent agent = new Agent(
            "MultiToolAgent",
            "An agent with multiple capabilities",
            "You are a helpful assistant with weather and text analysis capabilities.",
            Model.defaultModel,
            UUID.randomUUID().toString(),
            RequestParameters.defaultParameters,
            List.of(weatherTool, textTool)
        );
        
        // Test weather tool
        String weatherResponse = agent.text("What's the weather in San Francisco?");
        assertNotNull(weatherResponse);
        assertTrue(weatherResponse.toLowerCase().contains("fog") || 
                   weatherResponse.contains("65"));
        
        // Test text analysis tool
        String analysisResponse = agent.text(
            "Analyze this text: 'Hello world. This is a test sentence. How many words are there?'"
        );
        assertNotNull(analysisResponse);
        // Should mention word count (11 words) or sentence count (3 sentences)
        assertTrue(analysisResponse.contains("11") || 
                   analysisResponse.contains("word") ||
                   analysisResponse.contains("3"));
    }

    @Test
    public void testAgentExecutesToolWithLambdaFunction() throws Exception {
        // Create a tool using a lambda function
        Schema<String, String> schema = new ClassSchema<>(
            String.class,
            String.class
        );
        
        Function<String, String> reverseFunction = text -> 
            new StringBuilder(text).reverse().toString();
        
        Tool<String, String> reverseTool = Tool.create(
            "reverse_text",
            "Reverses the input text",
            schema,
            reverseFunction
        );
        
        // Create agent with the tool
        Agent agent = new Agent(
            "TextAgent",
            "An agent that manipulates text",
            "You are a text manipulation assistant. Use the available tools when asked.",
            Model.defaultModel,
            UUID.randomUUID().toString(),
            RequestParameters.defaultParameters,
            List.of(reverseTool)
        );
        
        // Test the tool execution
        String response = agent.text("Please reverse the text 'hello world'");
        assertNotNull(response);
        assertTrue(response.contains("dlrow olleh"));
    }

    @Test
    public void testAgentExecutesToolAsync() throws Exception {
        // Create a simple math tool
        Schema<Double, Double> schema = new ClassSchema<>(
            Double.class,
            Double.class
        );
        
        Tool<Double, Double> squareTool = Tool.create(
            "square",
            "Squares a number",
            schema,
            x -> x * x
        );
        
        // Create agent
        Agent agent = new Agent(
            "AsyncAgent",
            "An agent for async operations",
            "You are a math helper. Use the square tool when asked to square numbers.",
            Model.defaultModel,
            UUID.randomUUID().toString(),
            RequestParameters.defaultParameters,
            List.of(squareTool)
        );
        
        // Test async execution
        CompletableFuture<String> future = agent.textAsync("What is 7 squared?");
        String result = future.get();
        
        assertNotNull(result);
        assertTrue(result.contains("49"));
    }

    @Test
    public void testAgentSelectsCorrectToolFromMultiple() throws Exception {
        // Create multiple math tools
        Schema<Integer, Integer> intSchema = new ClassSchema<>(
            Integer.class,
            Integer.class
        );
        
        Tool<Integer, Integer> factorialTool = Tool.create(
            "factorial",
            "Calculates factorial of a number",
            intSchema,
            n -> {
                if (n <= 1) return 1;
                int result = 1;
                for (int i = 2; i <= n; i++) {
                    result *= i;
                }
                return result;
            }
        );
        
        Tool<Integer, Boolean> primeTool = Tool.create(
            "is_prime",
            "Checks if a number is prime",
            new ClassSchema<>(Integer.class, Boolean.class),
            n -> {
                if (n <= 1) return false;
                if (n <= 3) return true;
                if (n % 2 == 0 || n % 3 == 0) return false;
                for (int i = 5; i * i <= n; i += 6) {
                    if (n % i == 0 || n % (i + 2) == 0) return false;
                }
                return true;
            }
        );
        
        // Create agent with multiple tools
        Agent agent = new Agent(
            "MathSpecialistAgent",
            "An agent specializing in number theory",
            "You are a mathematics expert. Use the appropriate tool for factorial or prime number questions.",
            Model.defaultModel,
            UUID.randomUUID().toString(),
            RequestParameters.defaultParameters,
            List.of(factorialTool, primeTool)
        );
        
        // Test factorial tool selection
        String factorialResponse = agent.text("What is the factorial of 5?");
        assertNotNull(factorialResponse);
        assertTrue(factorialResponse.contains("120"));
        
        // Test prime tool selection
        String primeResponse = agent.text("Is 17 a prime number?");
        assertNotNull(primeResponse);
        assertTrue(primeResponse.toLowerCase().contains("yes") || 
                   primeResponse.toLowerCase().contains("true") ||
                   primeResponse.toLowerCase().contains("prime"));
    }

    @Test
    public void testAgentHandlesToolWithComplexInput() throws Exception {
        // Create a tool that takes complex input
        record SearchInput(String query, int maxResults, String sortBy) {}
        record SearchResult(String title, String description, double relevance) {}
        record SearchOutput(List<SearchResult> results, int totalCount) {}
        
        Schema<SearchInput, SearchOutput> schema = new ClassSchema<>(
            SearchInput.class,
            SearchOutput.class
        );
        
        Function<SearchInput, SearchOutput> searchFunction = input -> {
            // Simulate search results
            List<SearchResult> results = switch (input.query().toLowerCase()) {
                case "java" -> List.of(
                    new SearchResult("Java Programming", "Learn Java basics", 0.95),
                    new SearchResult("Java Best Practices", "Advanced Java tips", 0.85)
                );
                case "kotlin" -> List.of(
                    new SearchResult("Kotlin Guide", "Modern Kotlin development", 0.90),
                    new SearchResult("Kotlin Coroutines", "Async programming in Kotlin", 0.80)
                );
                default -> List.of(
                    new SearchResult("No results", "No matching documents found", 0.0)
                );
            };
            
            // Limit results based on maxResults
            if (input.maxResults() > 0 && results.size() > input.maxResults()) {
                results = results.subList(0, input.maxResults());
            }
            
            return new SearchOutput(results, results.size());
        };
        
        Tool<SearchInput, SearchOutput> searchTool = Tool.create(
            "search",
            "Searches for documents based on query",
            schema,
            searchFunction
        );
        
        // Create agent with search tool
        Agent agent = new Agent(
            "SearchAgent",
            "An agent that can search for information",
            "You are a search assistant. Use the search tool to find information. Always limit results to 2 items.",
            Model.defaultModel,
            UUID.randomUUID().toString(),
            RequestParameters.defaultParameters,
            List.of(searchTool)
        );
        
        // Test the search tool
        String response = agent.text("Search for Java programming resources");
        assertNotNull(response);
        assertTrue(response.toLowerCase().contains("java") || 
                   response.toLowerCase().contains("programming"));
    }

    @Test
    public void testAgentChainsMultipleToolCalls() throws Exception {
        // Create tools that can be chained
        Schema<String, Integer> lengthSchema = new ClassSchema<>(
            String.class,
            Integer.class
        );
        
        Tool<String, Integer> lengthTool = Tool.create(
            "get_length",
            "Gets the length of a string",
            lengthSchema,
            String::length
        );
        
        Schema<Integer, String> romanSchema = new ClassSchema<>(
            Integer.class,
            String.class
        );
        
        Tool<Integer, String> romanTool = Tool.create(
            "to_roman",
            "Converts number to Roman numerals",
            romanSchema,
            n -> {
                if (n <= 0 || n > 20) return "N/A";
                String[] romans = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X",
                                   "XI", "XII", "XIII", "XIV", "XV", "XVI", "XVII", "XVIII", "XIX", "XX"};
                return romans[n];
            }
        );
        
        // Create agent with both tools
        Agent agent = new Agent(
            "ChainAgent",
            "An agent that chains operations",
            "You are a helpful assistant. When asked about the length of text in Roman numerals, " +
            "first get the length, then convert to Roman numerals.",
            Model.defaultModel,
            UUID.randomUUID().toString(),
            RequestParameters.defaultParameters,
            List.of(lengthTool, romanTool)
        );
        
        // Test chained tool usage
        String response = agent.text(
            "What is the length of the word 'hello' in Roman numerals?"
        );
        assertNotNull(response);
        assertTrue(response.contains("V") || response.contains("5"));
    }
}