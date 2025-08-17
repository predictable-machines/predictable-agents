@file:Suppress("unused")

package predictable.samples

import predictable.AI
import predictable.Tool
import predictable.AI.Companion.unaryPlus

/**
 * Sample demonstrating how to use the unary plus operator to create an AI tool
 */
@DocumentationSample
suspend fun aiUnaryPlusSample() {
    val myTool: AI<String, String> = ({ input: String -> 
        "Processed: $input"
    }).unaryPlus()
    
    // Usage
    val result = myTool("Hello")
    println(result) // "Processed: Hello"
}

/**
 * Sample demonstrating how to generate a default name for an AI operation
 */
@DocumentationSample
fun aiNameGenerationSample() {
    data class UserInput(val userId: Int)
    data class UserProfile(val name: String, val email: String)
    
    // This will generate "fetchUserProfileByUserInput"
    val generatedName = AI.name<UserInput, UserProfile>()
    println(generatedName)
}

/**
 * Sample demonstrating how to generate a default description for an AI operation
 */
@DocumentationSample
fun aiDescriptionGenerationSample() {
    data class SearchQuery(val query: String)
    data class SearchResults(val items: List<String>)
    
    // This will generate "SearchQuery -> SearchResults"
    val generatedDescription = AI.description<SearchQuery, SearchResults>()
    println(generatedDescription)
}

/**
 * Sample demonstrating type name to identifier conversion
 */
@DocumentationSample
fun aiTypeNameToIdentifierSample() {
    // Converts complex type names to clean identifiers
    val identifier = AI.typeNameToIdentifier<List<String>>()
    println(identifier) // Will print a cleaned version like "List_String"
}