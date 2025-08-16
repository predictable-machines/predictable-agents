package predictable.agent.providers.openai

import com.aallam.openai.api.core.Usage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OpenAIProviderMetadataTest {

    @Test
    fun `AccumulatedUsage should correctly accumulate values`() {
        // Given
        val initialUsage = AccumulatedUsage()
        
        // When - using immutable operations
        val usage1 = initialUsage + Usage(promptTokens = 10, completionTokens = 5, totalTokens = 15)
        val usage2 = usage1 + Usage(promptTokens = 20, completionTokens = 10, totalTokens = 30)
        val finalUsage = usage2 + Usage(promptTokens = null, completionTokens = 15, totalTokens = null) // Handle nulls
        
        // Then
        assertEquals(30, finalUsage.promptTokens)
        assertEquals(30, finalUsage.completionTokens)
        assertEquals(45, finalUsage.totalTokens)
        
        // Verify original is unchanged (immutability)
        assertEquals(0, initialUsage.promptTokens)
        assertEquals(0, initialUsage.completionTokens)
        assertEquals(0, initialUsage.totalTokens)
        
        // Verify toUsage() conversion
        val convertedUsage = finalUsage.toUsage()
        assertNotNull(convertedUsage)
        assertEquals(30, convertedUsage.promptTokens)
        assertEquals(30, convertedUsage.completionTokens)
        assertEquals(45, convertedUsage.totalTokens)
    }

    @Test
    fun `AccumulatedUsage should handle null usage input`() {
        // Given
        val initialUsage = AccumulatedUsage()
        
        // When - using immutable operations
        val usage1 = initialUsage + null
        val usage2 = usage1 + Usage(promptTokens = 10, completionTokens = 5, totalTokens = 15)
        val finalUsage = usage2 + null
        
        // Then
        assertEquals(10, finalUsage.promptTokens)
        assertEquals(5, finalUsage.completionTokens)
        assertEquals(15, finalUsage.totalTokens)
        
        // Verify that adding null returns the same instance
        assertTrue(initialUsage + null === initialUsage, "Adding null should return the same instance")
    }

    @Test
    fun `AccumulatedUsage plus operator should create new instance`() {
        // Given
        val original = AccumulatedUsage(
            promptTokens = 100,
            completionTokens = 50,
            totalTokens = 150
        )
        
        // When - using the + operator
        val updated = original + Usage(promptTokens = 10, completionTokens = 5, totalTokens = 15)
        
        // Then
        // Original should not be modified (immutability)
        assertEquals(100, original.promptTokens)
        assertEquals(50, original.completionTokens)
        assertEquals(150, original.totalTokens)
        
        // New instance should have updated values
        assertEquals(110, updated.promptTokens)
        assertEquals(55, updated.completionTokens)
        assertEquals(165, updated.totalTokens)
    }

    @Test
    fun `AccumulatedUsage should start with zero values`() {
        // Given a new AccumulatedUsage instance
        val usage = AccumulatedUsage()
        
        // Then it should start with all zeros
        assertEquals(0, usage.promptTokens)
        assertEquals(0, usage.completionTokens)
        assertEquals(0, usage.totalTokens)
        
        // And toUsage() should also return zeros
        val converted = usage.toUsage()
        assertEquals(0, converted.promptTokens)
        assertEquals(0, converted.completionTokens)
        assertEquals(0, converted.totalTokens)
    }
    
    @Test
    fun `AccumulatedUsage should handle partial null values correctly`() {
        // Given
        val initialUsage = AccumulatedUsage()
        
        // When adding usage with some null values using immutable operations
        val usage1 = initialUsage + Usage(promptTokens = 10, completionTokens = null, totalTokens = null)
        val usage2 = usage1 + Usage(promptTokens = null, completionTokens = 5, totalTokens = null)
        val finalUsage = usage2 + Usage(promptTokens = null, completionTokens = null, totalTokens = 25)
        
        // Then it should only add non-null values
        assertEquals(10, finalUsage.promptTokens)
        assertEquals(5, finalUsage.completionTokens)
        assertEquals(25, finalUsage.totalTokens) // Note: totalTokens is tracked separately, not computed
    }
    
    @Test
    fun `AccumulatedUsage fromUsages should combine multiple usages`() {
        // Given multiple usage objects
        val usage1 = Usage(promptTokens = 10, completionTokens = 5, totalTokens = 15)
        val usage2 = Usage(promptTokens = 20, completionTokens = 10, totalTokens = 30)
        val usage3 = Usage(promptTokens = 30, completionTokens = 15, totalTokens = 45)
        
        // When combining them using the companion function
        val combined = AccumulatedUsage.fromUsages(usage1, null, usage2, usage3)
        
        // Then
        assertEquals(60, combined.promptTokens)
        assertEquals(30, combined.completionTokens)
        assertEquals(90, combined.totalTokens)
    }
}