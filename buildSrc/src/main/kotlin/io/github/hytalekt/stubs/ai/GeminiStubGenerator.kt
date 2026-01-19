package io.github.hytalekt.stubs.ai

/**
 * Result of batch stub generation, including both successful stubs and skipped files.
 */
data class StubGenerationResult(
    /** Successfully generated stubs: class name to stub source */
    val stubs: Map<String, String>,
    /** Files that were skipped due to length limits: class name to reason */
    val skipped: Map<String, SkipReason>,
) {
    fun printReport() {
        if (skipped.isNotEmpty()) {
            println("\n========== SKIPPED FILES REPORT ==========")
            println("The following ${skipped.size} file(s) were skipped and require manual review:\n")
            skipped.entries
                .sortedByDescending { it.value.characterCount }
                .forEach { (className, reason) ->
                    println("  - $className")
                    println("      Reason: ${reason.reason}")
                    println("      Size: ${reason.characterCount} chars, ${reason.lineCount} lines")
                }
            println("\n===========================================")
        }
    }
}

data class SkipReason(
    val reason: String,
    val characterCount: Int,
    val lineCount: Int,
)

/**
 * Uses Gemini via OpenRouter to generate improved stubs from decompiled Java code.
 *
 * Features:
 * - Renames var0, var1, etc. parameters to meaningful names
 * - Adds Javadoc documentation
 * - Replaces method bodies with GeneratedStubException()
 */
class GeminiStubGenerator(
    private val client: OpenRouterClient,
    private val maxCharacters: Int = DEFAULT_MAX_CHARACTERS,
    private val maxLines: Int = DEFAULT_MAX_LINES,
) {
    companion object {
        const val DEFAULT_MAX_CHARACTERS = 50_000
        const val DEFAULT_MAX_LINES = 1500

        private val SYSTEM_PROMPT =
            """
            |You are a **Java API stub generator**. You take decompiled Java sources as input and output **sanitized stubs** that preserve API shape but contain **no original implementation or executable logic**.
            |
            |CRITICAL OUTPUT RULES (HARD REQUIREMENTS)
            |- Output MUST be **only Java source text**.
            |- Output MUST NOT include markdown fences or backticks of any kind. Do NOT output “```”, “```java”, or surrounding formatting.
            |- Do NOT add commentary, explanations, or annotations not present in the input.
            |- Do NOT invent new members, new classes, new methods, or new overloads. Only emit what exists in the provided input.
            |
            |PRIMARY GOAL
            |- Preserve the public/protected API surface (types, signatures, modifiers, nested type structure), but **remove all implementations**.
            |
            |PRESERVE EXACTLY (byte-for-byte where possible)
            |- package declaration
            |- all import statements (do not add/remove/reorder)
            |- class/interface/enum/record/annotation declarations, names, and modifiers
            |- extends/implements clauses
            |- generic parameters and bounds
            |- method signatures EXACTLY: name, return type, parameter types/order, type params, throws clauses, varargs
            |- field declarations: name, type, modifiers (but see initializer rules below)
            |- all existing annotations and their values (@Deprecated, @Override, @Nonnull, etc.)
            |- enum constants and their arguments
            |- annotation default values
            |
            |RENAMING (ONLY WHEN NECESSARY)
            |- Rename ONLY identifiers that are clearly decompiler placeholders/obfuscation:
            |  - parameters like: var0/var1/arg0/param0/p0
            |  - fields like: field_1234/f_1234
            |  - meaningless locals IF they remain in signatures (rare)
            |- Never rename public API method names, class names, or nested type names.
            |- Use descriptive names based on type/usage.
            |
            |JAVADOC
            |- Add Javadoc to all public/protected classes, fields, constructors, and methods.
            |- Keep Javadoc factual and generic; do NOT describe original algorithms.
            |- Include @param and @return where applicable.
            |- Do NOT add @throws for io.github.kytale.stubs.GeneratedStubException.
            |
            |IMPLEMENTATION REMOVAL (ABSOLUTE)
            |- Replace ALL non-abstract, non-native method bodies with a stub that throws:
            |  `throw new io.github.kytale.stubs.GeneratedStubException();`
            |- This includes: private methods, protected methods, public methods, static methods, interface default methods, interface static methods, and methods in nested classes.
            |- For void methods: same throw statement.
            |- For constructors:
            |  - Preserve an explicit first-statement `super(...)` or `this(...)` call EXACTLY if present in the input.
            |  - After that, throw GeneratedStubException.
            |  - If there is no explicit super/this call, do not add one; just throw.
            |- For static initializers: replace content with ONLY the throw statement.
            |- For instance initializers: replace content with ONLY the throw statement.
            |- Abstract methods: no body.
            |- Native methods: no body (keep `native`).
            |
            |FIELD INITIALIZER RULES (NO LOGIC, NO RECONSTRUCTION)
            |- Keep initializer ONLY if it is a compile-time constant literal:
            |  - primitives, String literals, or other Java compile-time constants (e.g., `static final int X = 5;`)
            |- Otherwise, the initializer MUST be replaced with a type-appropriate default:
            |  - object/reference types: `= null;`
            |  - boolean: `= false;`
            |  - numeric primitives: `= 0;` / `= 0L;` / `= 0.0F;` / `= 0.0D;`
            |  - char: `= '\u0000';`
            |- This rule applies EVEN to `static final` fields. If it is not a literal compile-time constant, it MUST become default (usually `null`).
            |- Do NOT create `new ...(...)`, lambdas, method references, factory calls, array creations, or any expression with side effects in field initializers.
            |- Examples that MUST become defaults:
            |  - `public static final Codec<X> CODEC = new XCodec();` → `public static final Codec<X> CODEC = null;`
            |  - `public static final Codec<X[]> ARRAY = new ArrayCodec<>(CODEC, X[]::new);` → `... = null;`
            |  - `private final BsonDocument bson;` must remain as-is (no initializer added).
            |
            |NO “LEFTOVER” IMPLEMENTATION
            |- Do not keep assignments like `this.bson = bsonDocument;`
            |- Do not keep returns like `return this.bson;`
            |- Do not keep control flow, loops, synchronization, or any other logic.
            |- The ONLY permitted statement inside stubbed bodies is the single throw statement (except for preserved super/this call in constructors).
            |
            |VALIDATION BEFORE YOU OUTPUT
            |- Ensure there are ZERO occurrences of “```” or backticks.
            |- Ensure every non-abstract, non-native body contains only the stub throw (plus allowed super/this first line in constructors).
            |- Ensure no non-literal field initializers remain.
            |
            |OUTPUT
            |- Return ONLY the final Java source code text.
            """.trimMargin()
    }

    /**
     * Generate an improved stub from decompiled Java source code.
     *
     * @param decompiledSource The decompiled Java source code
     * @param className The fully qualified class name (for context)
     * @return Improved Java source code with documentation and stub bodies
     */
    fun generateStub(decompiledSource: String): String =
        client
            .complete(decompiledSource, SYSTEM_PROMPT)
            .trim()
            .removePrefix("```java")
            .removeSuffix("```")
            .trim()

    /**
     * Check if a source file exceeds the configured size limits.
     *
     * @return SkipReason if the file should be skipped, null otherwise
     */
    private fun checkSizeLimit(source: String): SkipReason? {
        val charCount = source.length
        val lineCount = source.lines().size

        return when {
            charCount > maxCharacters ->
                SkipReason(
                    reason = "Exceeds character limit ($charCount > $maxCharacters)",
                    characterCount = charCount,
                    lineCount = lineCount,
                )
            lineCount > maxLines ->
                SkipReason(
                    reason = "Exceeds line limit ($lineCount > $maxLines)",
                    characterCount = charCount,
                    lineCount = lineCount,
                )
            else -> null
        }
    }

    /**
     * Generate stubs for multiple classes in batch with parallel API calls.
     * Files exceeding size limits are skipped and reported.
     *
     * @param decompiledSources Map of class name to decompiled source code
     * @param maxConcurrency Maximum number of concurrent API requests (default 10)
     * @param progressCallback Optional callback for progress updates (called after all complete)
     * @return StubGenerationResult containing generated stubs and skipped files report
     */
    fun generateStubs(
        decompiledSources: Map<String, String>,
        maxConcurrency: Int = 10,
        progressCallback: ((className: String, current: Int, total: Int) -> Unit)? = null,
    ): StubGenerationResult {
        if (decompiledSources.isEmpty()) {
            return StubGenerationResult(emptyMap(), emptyMap())
        }

        // Partition into processable and skipped based on size limits
        val skipped = mutableMapOf<String, SkipReason>()
        val toProcess = mutableMapOf<String, String>()

        for ((className, source) in decompiledSources) {
            val skipReason = checkSizeLimit(source)
            if (skipReason != null) {
                skipped[className] = skipReason
            } else {
                toProcess[className] = source
            }
        }

        if (toProcess.isEmpty()) {
            return StubGenerationResult(emptyMap(), skipped)
        }

        val entries = toProcess.entries.toList()
        val total = entries.size

        // Build all prompts for files within size limits
        val requests =
            entries.map { (_, source) ->
                source to SYSTEM_PROMPT
            }

        // Execute all requests in parallel
        val responses = client.completeAll(requests, maxConcurrency)

        // Build results map and invoke progress callbacks
        val results = mutableMapOf<String, String>()
        entries.forEachIndexed { index, (className, _) ->
            val response =
                responses[index]
                    .trim()
                    .removePrefix("```java")
                    .removeSuffix("```")
                    .trim()
            results[className] = response
            progressCallback?.invoke(className, index + 1, total)
        }

        return StubGenerationResult(results, skipped)
    }

    /**
     * Compute the cache key for a given source.
     * Useful for checking if a response is cached before making a request.
     */
    fun computeCacheKey(decompiledSource: String): String = client.computeCacheKey(decompiledSource, SYSTEM_PROMPT)
}
