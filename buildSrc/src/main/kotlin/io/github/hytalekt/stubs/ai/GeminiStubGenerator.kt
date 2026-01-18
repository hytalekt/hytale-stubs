package io.github.hytalekt.stubs.ai

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
) {
    companion object {
        private val SYSTEM_PROMPT = """
            |You are a Java code documentation and refactoring assistant. Your task is to improve decompiled Java code by:
            |
            |1. Renaming generic parameter names (var0, var1, param0, param1, etc.) to meaningful, descriptive names based on their usage and type
            |2. Adding comprehensive Javadoc documentation to classes, methods, and fields
            |3. Replacing all method bodies with: throw new io.github.kytale.stubs.GeneratedStubException();
            |4. Keeping the exact same method signatures (return types, parameter types, modifiers, annotations)
            |5. Preserving all imports, package declarations, and class structure
            |
            |IMPORTANT RULES:
            |- ALWAYS replace method bodies with: throw new io.github.kytale.stubs.GeneratedStubException();
            |- Do NOT add @throws tags for GeneratedStubException in Javadoc
            |- For constructors, call super() with appropriate default values if needed, then throw the exception
            |- Keep interface methods abstract (no body)
            |- Keep abstract methods abstract (no body)
            |- For default interface methods, replace body with the exception throw
            |- Preserve all annotations exactly as they are
            |- Keep constant field initializers (static final with literal values)
            |- For non-constant fields, keep appropriate default initializers
            |
            |Return ONLY the improved Java source code, nothing else. No markdown formatting, no explanations.
        """.trimMargin()
    }

    /**
     * Generate an improved stub from decompiled Java source code.
     *
     * @param decompiledSource The decompiled Java source code
     * @param className The fully qualified class name (for context)
     * @return Improved Java source code with documentation and stub bodies
     */
    fun generateStub(decompiledSource: String, className: String): String {
        val prompt = buildString {
            appendLine("Improve the following decompiled Java class: $className")
            appendLine()
            appendLine("```java")
            appendLine(decompiledSource)
            appendLine("```")
        }

        return client.complete(prompt, SYSTEM_PROMPT)
            .trim()
            .removePrefix("```java")
            .removeSuffix("```")
            .trim()
    }

    /**
     * Generate stubs for multiple classes in batch.
     * Each class is processed independently to maintain cache effectiveness.
     */
    fun generateStubs(
        decompiledSources: Map<String, String>,
        progressCallback: ((className: String, current: Int, total: Int) -> Unit)? = null,
    ): Map<String, String> {
        val results = mutableMapOf<String, String>()
        val total = decompiledSources.size
        var current = 0

        for ((className, source) in decompiledSources) {
            current++
            progressCallback?.invoke(className, current, total)
            results[className] = generateStub(source, className)
        }

        return results
    }

    /**
     * Compute the cache key for a given source and class name.
     * Useful for checking if a response is cached before making a request.
     */
    fun computeCacheKey(decompiledSource: String, className: String): String {
        val prompt = buildString {
            appendLine("Improve the following decompiled Java class: $className")
            appendLine()
            appendLine("```java")
            appendLine(decompiledSource)
            appendLine("```")
        }
        return client.computeCacheKey(prompt, SYSTEM_PROMPT)
    }
}
