package predictable.samples

/**
 * Marks declarations that are used only for documentation samples.
 * These are not part of the public API and should not be used in production code.
 */
@RequiresOptIn(
    message = "This is a documentation sample and not part of the public API",
    level = RequiresOptIn.Level.ERROR
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
annotation class DocumentationSample