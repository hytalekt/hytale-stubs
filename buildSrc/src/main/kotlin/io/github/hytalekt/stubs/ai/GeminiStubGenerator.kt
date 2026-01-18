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
            |You are a Java code stub generator. Transform decompiled Java code into clean, documented API stubs.
            |
            |## YOUR TASKS
            |
            |1. **Rename identifiers** - Replace obfuscated/generic names with meaningful ones:
            |   - Parameters: var0, var1, param0, arg0, p0 → descriptive names based on type and usage
            |   - Fields: field_1234, f_1234 → descriptive names based on type and usage
            |   - Local variables in surviving code: same treatment
            |
            |2. **Add Javadoc** - Document all public/protected API elements:
            |   - Classes: purpose, usage notes
            |   - Methods: what it does, @param for each parameter, @return if non-void
            |   - Fields: what it stores
            |   - Do NOT add @throws for GeneratedStubException
            |
            |3. **Replace method bodies** with stub implementation (see rules below)
            |
            |## STUB BODY RULES
            |
            |**Regular methods** (non-void):
            |```java
            |public String getName() {
            |    throw new io.github.kytale.stubs.GeneratedStubException();
            |}
            |```
            |
            |**Void methods**:
            |```java
            |public void doSomething() {
            |    throw new io.github.kytale.stubs.GeneratedStubException();
            |}
            |```
            |
            |**Constructors** - MUST preserve super() or this() calls, then throw:
            |```java
            |// If original has super(x, y) call:
            |public MyClass(int value) {
            |    super(value, null);  // Keep original super() arguments
            |    throw new io.github.kytale.stubs.GeneratedStubException();
            |}
            |
            |// If original has this() constructor chaining:
            |public MyClass() {
            |    this(0);  // Keep original this() call
            |    throw new io.github.kytale.stubs.GeneratedStubException();
            |}
            |
            |// If no explicit super/this (implicit super()):
            |public MyClass(String name) {
            |    throw new io.github.kytale.stubs.GeneratedStubException();
            |}
            |```
            |
            |**Static initializers**:
            |```java
            |static {
            |    throw new io.github.kytale.stubs.GeneratedStubException();
            |}
            |```
            |
            |**Instance initializers**:
            |```java
            |{
            |    throw new io.github.kytale.stubs.GeneratedStubException();
            |}
            |```
            |
            |## PRESERVE EXACTLY (do not modify)
            |
            |- Package declarations
            |- Import statements
            |- Class/interface/enum/record/annotation declarations and modifiers
            |- Method signatures: return types, parameter types, type parameters, throws clauses
            |- All annotations and their values (@Override, @Deprecated, @FunctionalInterface, etc.)
            |- Field types and modifiers
            |- Constant initializers: static final fields with compile-time constant values
            |- Enum constants and their arguments
            |- Annotation default values
            |- extends/implements clauses
            |- Generics and type bounds
            |- Varargs (...)
            |- Inner/nested class structure
            |
            |## SPECIAL CASES
            |
            |**Abstract methods** - NO body:
            |```java
            |public abstract void process();
            |```
            |
            |**Interface methods** (non-default) - NO body:
            |```java
            |void process();
            |String getName();
            |```
            |
            |**Interface default methods** - stub body:
            |```java
            |default void process() {
            |    throw new io.github.kytale.stubs.GeneratedStubException();
            |}
            |```
            |
            |**Interface static methods** - stub body:
            |```java
            |static Helper create() {
            |    throw new io.github.kytale.stubs.GeneratedStubException();
            |}
            |```
            |
            |**Native methods** - NO body, keep native modifier:
            |```java
            |public native void nativeCall();
            |```
            |
            |**Enum constructors** - private, stub body:
            |```java
            |private MyEnum(int value) {
            |    throw new io.github.kytale.stubs.GeneratedStubException();
            |}
            |```
            |
            |**Record compact constructors**:
            |```java
            |public record Point(int x, int y) {
            |    public Point {
            |        throw new io.github.kytale.stubs.GeneratedStubException();
            |    }
            |}
            |```
            |
            |**Annotation methods** - NO body, preserve defaults:
            |```java
            |String value() default "";
            |int count();
            |```
            |
            |**Lambda fields** - keep as null or functional interface stub:
            |```java
            |private final Function<String, Integer> parser = null;
            |```
            |
            |## FIELD INITIALIZERS
            |
            |- **static final primitives/Strings with literals**: KEEP the value
            |  ```java
            |  public static final int MAX_SIZE = 100;
            |  public static final String NAME = "default";
            |  ```
            |
            |- **Other fields**: use type-appropriate defaults
            |  ```java
            |  private int count = 0;
            |  private String name = null;
            |  private List<String> items = null;
            |  private boolean enabled = false;
            |  ```
            |
            |## OUTPUT FORMAT
            |
            |Return ONLY the Java source code. No markdown code fences. No explanations. No comments about what you changed.
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
