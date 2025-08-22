package predictable;

import org.junit.Test;
import predictable.tool.Schema;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.junit.Assert.*;

/**
 * Tests for Tool interoperability with Java, demonstrating usage with regular Java Functions (non-suspending).
 */
public class ToolJavaTest {

    // Test data classes as records
    record TextInput(String text) {}
    record WordCount(int words, int characters) {}
    
    record MathInput(double a, double b, String operation) {}
    record MathResult(double result, String operation) {}

    @Test
    public void testToolWithJavaFunction() {
        // Create a schema for the word counting tool
        Schema<TextInput, WordCount> schema = new ClassSchema<>(
            TextInput.class,
            WordCount.class
        );

        // Create a tool using a Java Function (non-suspending)
        Function<TextInput, WordCount> wordCounter = input -> {
            String text = input.text();
            String[] words = text.trim().split("\\s+");
            int wordCount = text.trim().isEmpty() ? 0 : words.length;
            int charCount = text.length();
            return new WordCount(wordCount, charCount);
        };

        Tool<TextInput, WordCount> wordCountTool = Tool.create(
            "word_counter",
            "Counts words and characters in text",
            schema,
            wordCounter
        );

        // Test the tool
        TextInput input = new TextInput("Hello world from Java!");
        WordCount result = wordCountTool.invokeBlocking(input);

        assertEquals(4, result.words());
        assertEquals(22, result.characters());
        assertEquals("word_counter", wordCountTool.getName());
        assertEquals("Counts words and characters in text", wordCountTool.getDescription());
    }

    @Test
    public void testToolWithMathOperations() {
        // Create a schema for the math tool
        Schema<MathInput, MathResult> schema = new ClassSchema<>(
            MathInput.class,
            MathResult.class
        );

        // Create a math calculator tool
        Function<MathInput, MathResult> calculator = input -> {
            double a = input.a();
            double b = input.b();
            String op = input.operation();
            
            double result = switch (op.toLowerCase()) {
                case "add", "+" -> a + b;
                case "subtract", "-" -> a - b;
                case "multiply", "*" -> a * b;
                case "divide", "/" -> b != 0 ? a / b : Double.NaN;
                case "power", "^" -> Math.pow(a, b);
                default -> Double.NaN;
            };
            
            return new MathResult(result, op);
        };

        Tool<MathInput, MathResult> mathTool = Tool.create(
            "calculator",
            "Performs basic math operations",
            schema,
            calculator,
            "math-tool-123"  // Custom ID
        );

        // Test various operations
        MathResult addResult = mathTool.invokeBlocking(new MathInput(10, 5, "add"));
        assertEquals(15.0, addResult.result(), 0.001);

        MathResult multiplyResult = mathTool.invokeBlocking(new MathInput(7, 8, "multiply"));
        assertEquals(56.0, multiplyResult.result(), 0.001);

        MathResult divideResult = mathTool.invokeBlocking(new MathInput(20, 4, "divide"));
        assertEquals(5.0, divideResult.result(), 0.001);

        MathResult powerResult = mathTool.invokeBlocking(new MathInput(2, 3, "power"));
        assertEquals(8.0, powerResult.result(), 0.001);

        // Verify tool metadata
        assertEquals("calculator", mathTool.getName());
        assertEquals("Performs basic math operations", mathTool.getDescription());
        assertEquals("math-tool-123", mathTool.getId());
    }

    @Test
    public void testToolWithStringTransformation() {
        // Create a simple string transformation tool
        Schema<String, String> schema = new ClassSchema<>(
            String.class,
            String.class
        );

        // Create various string transformation tools
        Tool<String, String> upperCaseTool = Tool.create(
            "to_uppercase",
            "Converts text to uppercase",
            schema,
            String::toUpperCase
        );

        Tool<String, String> reverseTool = Tool.create(
            "reverse",
            "Reverses the input string",
            schema,
            input -> new StringBuilder(input).reverse().toString()
        );

        // Test the tools
        assertEquals("HELLO WORLD", upperCaseTool.invokeBlocking("hello world"));
        assertEquals("dlrow olleh", reverseTool.invokeBlocking("hello world"));
    }

    @Test
    public void testToolChaining() {
        // Create schemas
        Schema<String, Integer> lengthSchema = new ClassSchema<>(
            String.class,
            Integer.class
        );
        
        Schema<Integer, String> formatSchema = new ClassSchema<>(
            Integer.class,
            String.class
        );

        // Create tools that can be chained
        Tool<String, Integer> lengthTool = Tool.create(
            "string_length",
            "Gets the length of a string",
            lengthSchema,
            String::length
        );

        Tool<Integer, String> formatTool = Tool.create(
            "format_number",
            "Formats a number as a string message",
            formatSchema,
            num -> String.format("The number is: %d", num)
        );

        // Chain the tools manually
        String input = "Hello, World!";
        Integer length = lengthTool.invokeBlocking(input);
        String formatted = formatTool.invokeBlocking(length);

        assertEquals(13, length.intValue());
        assertEquals("The number is: 13", formatted);
    }

    @Test
    public void testToolWithComplexLogic() {
        // Test record for email validation
        record EmailValidation(String email, boolean isValid, String reason) {}
        
        Schema<String, EmailValidation> schema = new ClassSchema<>(
            String.class,
            EmailValidation.class
        );

        // Create an email validation tool
        Function<String, EmailValidation> emailValidator = email -> {
            if (email == null || email.isEmpty()) {
                return new EmailValidation(email, false, "Email is empty");
            }
            
            if (!email.contains("@")) {
                return new EmailValidation(email, false, "Missing @ symbol");
            }
            
            String[] parts = email.split("@");
            if (parts.length != 2) {
                return new EmailValidation(email, false, "Invalid format");
            }
            
            if (!parts[1].contains(".")) {
                return new EmailValidation(email, false, "Invalid domain");
            }
            
            return new EmailValidation(email, true, "Valid email");
        };

        Tool<String, EmailValidation> validationTool = Tool.create(
            "email_validator",
            "Validates email addresses",
            schema,
            emailValidator
        );

        // Test various email formats
        EmailValidation valid = validationTool.invokeBlocking("user@example.com");
        assertTrue(valid.isValid());
        assertEquals("Valid email", valid.reason());

        EmailValidation noAt = validationTool.invokeBlocking("userexample.com");
        assertFalse(noAt.isValid());
        assertEquals("Missing @ symbol", noAt.reason());

        EmailValidation noDomain = validationTool.invokeBlocking("user@example");
        assertFalse(noDomain.isValid());
        assertEquals("Invalid domain", noDomain.reason());

        EmailValidation empty = validationTool.invokeBlocking("");
        assertFalse(empty.isValid());
        assertEquals("Email is empty", empty.reason());
    }

    @Test
    public void testToolWithLambdaExpression() {
        // Create a tool directly with a lambda expression
        Schema<Integer, Boolean> schema = new ClassSchema<>(
            Integer.class,
            Boolean.class
        );

        Tool<Integer, Boolean> isEvenTool = Tool.create(
            "is_even",
            "Checks if a number is even",
            schema,
            n -> n % 2 == 0
        );

        assertTrue(isEvenTool.invokeBlocking(4));
        assertFalse(isEvenTool.invokeBlocking(7));
        assertTrue(isEvenTool.invokeBlocking(0));
        assertFalse(isEvenTool.invokeBlocking(-3));
    }

    // Static utility class with methods to use as tools
    static class MathUtils {
        public static Double square(Double x) {
            return x * x;
        }
        
        public static Integer factorial(Integer n) {
            if (n <= 1) return 1;
            int result = 1;
            for (int i = 2; i <= n; i++) {
                result *= i;
            }
            return result;
        }
        
        public static Boolean isPrime(Integer n) {
            if (n <= 1) return false;
            if (n <= 3) return true;
            if (n % 2 == 0 || n % 3 == 0) return false;
            for (int i = 5; i * i <= n; i += 6) {
                if (n % i == 0 || n % (i + 2) == 0) return false;
            }
            return true;
        }
    }
    
    static class StringUtils {
        public static String reverse(String input) {
            return new StringBuilder(input).reverse().toString();
        }
        
        public static Integer countVowels(String text) {
            return (int) text.toLowerCase()
                .chars()
                .filter(ch -> "aeiou".indexOf(ch) != -1)
                .count();
        }
        
        public static String toTitleCase(String text) {
            if (text == null || text.isEmpty()) return text;
            String[] words = text.split("\\s+");
            StringBuilder result = new StringBuilder();
            for (String word : words) {
                if (!word.isEmpty()) {
                    if (result.length() > 0) result.append(" ");
                    result.append(Character.toUpperCase(word.charAt(0)))
                          .append(word.substring(1).toLowerCase());
                }
            }
            return result.toString();
        }
    }
    
    @Test
    public void testToolFromStaticMethod() {
        // Create tool from static method reference
        Schema<Double, Double> squareSchema = new ClassSchema<>(
            Double.class,
            Double.class
        );
        
        Tool<Double, Double> squareTool = Tool.create(
            "square",
            "Squares a number",
            squareSchema,
            MathUtils::square
        );
        
        assertEquals(25.0, squareTool.invokeBlocking(5.0), 0.001);
        assertEquals(144.0, squareTool.invokeBlocking(12.0), 0.001);
        assertEquals(2.25, squareTool.invokeBlocking(1.5), 0.001);
    }
    
    @Test
    public void testToolFromStaticMethodWithComplexLogic() {
        // Create factorial tool
        Schema<Integer, Integer> factorialSchema = new ClassSchema<>(
            Integer.class,
            Integer.class
        );
        
        Tool<Integer, Integer> factorialTool = Tool.create(
            "factorial",
            "Calculates factorial of a number",
            factorialSchema,
            MathUtils::factorial
        );
        
        assertEquals(Integer.valueOf(1), factorialTool.invokeBlocking(0));
        assertEquals(Integer.valueOf(1), factorialTool.invokeBlocking(1));
        assertEquals(Integer.valueOf(120), factorialTool.invokeBlocking(5));
        assertEquals(Integer.valueOf(3628800), factorialTool.invokeBlocking(10));
    }
    
    @Test
    public void testToolFromStaticBooleanMethod() {
        // Create prime checker tool
        Schema<Integer, Boolean> primeSchema = new ClassSchema<>(
            Integer.class,
            Boolean.class
        );
        
        Tool<Integer, Boolean> primeTool = Tool.create(
            "is_prime",
            "Checks if a number is prime",
            primeSchema,
            MathUtils::isPrime
        );
        
        assertFalse(primeTool.invokeBlocking(1));
        assertTrue(primeTool.invokeBlocking(2));
        assertTrue(primeTool.invokeBlocking(17));
        assertFalse(primeTool.invokeBlocking(100));
        assertTrue(primeTool.invokeBlocking(97));
    }
    
    @Test
    public void testToolFromStaticStringMethod() {
        // Test string reversal
        Schema<String, String> reverseSchema = new ClassSchema<>(
            String.class,
            String.class
        );
        
        Tool<String, String> reverseTool = Tool.create(
            "reverse_string",
            "Reverses a string",
            reverseSchema,
            StringUtils::reverse
        );
        
        assertEquals("olleh", reverseTool.invokeBlocking("hello"));
        assertEquals("!dlrow olleH", reverseTool.invokeBlocking("Hello world!"));
        assertEquals("", reverseTool.invokeBlocking(""));
    }
    
    @Test
    public void testToolFromStaticMethodReturningPrimitive() {
        // Test vowel counting
        Schema<String, Integer> vowelSchema = new ClassSchema<>(
            String.class,
            Integer.class
        );
        
        Tool<String, Integer> vowelTool = Tool.create(
            "count_vowels",
            "Counts vowels in text",
            vowelSchema,
            StringUtils::countVowels
        );
        
        assertEquals(Integer.valueOf(2), vowelTool.invokeBlocking("hello"));
        assertEquals(Integer.valueOf(5), vowelTool.invokeBlocking("aeiou"));
        assertEquals(Integer.valueOf(0), vowelTool.invokeBlocking("xyz"));
        assertEquals(Integer.valueOf(3), vowelTool.invokeBlocking("HELLO WORLD"));
    }
    
    @Test
    public void testToolFromStaticMethodWithNullHandling() {
        // Test title case conversion
        Schema<String, String> titleCaseSchema = new ClassSchema<>(
            String.class,
            String.class
        );
        
        Tool<String, String> titleCaseTool = Tool.create(
            "to_title_case",
            "Converts text to title case",
            titleCaseSchema,
            StringUtils::toTitleCase
        );
        
        assertEquals("Hello World", titleCaseTool.invokeBlocking("hello world"));
        assertEquals("The Quick Brown Fox", titleCaseTool.invokeBlocking("the quick brown fox"));
        assertEquals("", titleCaseTool.invokeBlocking(""));
        assertEquals("A", titleCaseTool.invokeBlocking("a"));
    }
    
    @Test
    public void testChainingToolsFromStaticMethods() {
        // Chain multiple static method tools
        Schema<String, String> reverseSchema = new ClassSchema<>(
            String.class,
            String.class
        );
        
        Schema<String, String> titleSchema = new ClassSchema<>(
            String.class,
            String.class
        );
        
        Tool<String, String> reverseTool = Tool.create(
            "reverse",
            "Reverses string",
            reverseSchema,
            StringUtils::reverse
        );
        
        Tool<String, String> titleTool = Tool.create(
            "title_case",
            "Converts to title case",
            titleSchema,
            StringUtils::toTitleCase
        );
        
        // Chain: reverse then title case
        String input = "hello world";
        String reversed = reverseTool.invokeBlocking(input);
        String titleCased = titleTool.invokeBlocking(reversed);
        
        assertEquals("dlrow olleh", reversed);
        assertEquals("Dlrow Olleh", titleCased);
    }
    
    @Test
    public void testToolFromJavaUtilityStaticMethods() {
        // Use Java's built-in static methods
        Schema<String, Integer> parseSchema = new ClassSchema<>(
            String.class,
            Integer.class
        );
        
        Tool<String, Integer> parseTool = Tool.create(
            "parse_int",
            "Parses string to integer",
            parseSchema,
            Integer::parseInt
        );
        
        assertEquals(Integer.valueOf(42), parseTool.invokeBlocking("42"));
        assertEquals(Integer.valueOf(-100), parseTool.invokeBlocking("-100"));
        assertEquals(Integer.valueOf(0), parseTool.invokeBlocking("0"));
        
        // Test with Math static methods
        Schema<Double, Double> absSchema = new ClassSchema<>(
            Double.class,
            Double.class
        );
        
        Tool<Double, Double> absTool = Tool.create(
            "absolute",
            "Gets absolute value",
            absSchema,
            Math::abs
        );
        
        assertEquals(5.0, absTool.invokeBlocking(-5.0), 0.001);
        assertEquals(3.14, absTool.invokeBlocking(3.14), 0.001);
        assertEquals(0.0, absTool.invokeBlocking(0.0), 0.001);
    }
    
    @Test
    public void testToolWithAsyncInvoke() throws Exception {
        // Create a schema for text processing
        Schema<String, String> schema = new ClassSchema<>(
            String.class,
            String.class
        );
        
        // Create a tool that simulates some processing
        Tool<String, String> processingTool = Tool.create(
            "text_processor",
            "Processes text asynchronously",
            schema,
            text -> {
                // Simulate some processing
                return "Processed: " + text.toUpperCase();
            }
        );
        
        // Test async invocation
        CompletableFuture<? extends String> future = processingTool.invokeAsync("hello async");
        
        // Wait for the result
        String result = future.get(5, TimeUnit.SECONDS);
        
        assertEquals("Processed: HELLO ASYNC", result);
        
        // Test multiple async invocations
        CompletableFuture<? extends String> future1 = processingTool.invokeAsync("first");
        CompletableFuture<? extends String> future2 = processingTool.invokeAsync("second");
        CompletableFuture<? extends String> future3 = processingTool.invokeAsync("third");
        
        // Wait for all results
        String result1 = future1.get(5, TimeUnit.SECONDS);
        String result2 = future2.get(5, TimeUnit.SECONDS);
        String result3 = future3.get(5, TimeUnit.SECONDS);
        
        assertEquals("Processed: FIRST", result1);
        assertEquals("Processed: SECOND", result2);
        assertEquals("Processed: THIRD", result3);
    }
    
    @Test
    public void testToolIdGeneration() {
        Schema<String, String> schema = new ClassSchema<>(
            String.class,
            String.class
        );

        // Create tool without specifying ID (should auto-generate)
        Tool<String, String> tool1 = Tool.create(
            "tool1",
            "First tool",
            schema,
            Function.identity()
        );

        // Create another tool without ID
        Tool<String, String> tool2 = Tool.create(
            "tool2",
            "Second tool",
            schema,
            Function.identity()
        );

        // IDs should be generated and different
        assertNotNull(tool1.getId());
        assertNotNull(tool2.getId());
        assertNotEquals(tool1.getId(), tool2.getId());

        // Create tool with explicit ID
        Tool<String, String> tool3 = Tool.create(
            "tool3",
            "Third tool",
            schema,
            Function.identity(),
            "custom-id-123"
        );

        assertEquals("custom-id-123", tool3.getId());
    }
}