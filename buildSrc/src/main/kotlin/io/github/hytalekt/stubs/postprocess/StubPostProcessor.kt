package io.github.hytalekt.stubs.postprocess

import com.github.javaparser.JavaParser
import com.github.javaparser.ParseProblemException
import com.github.javaparser.ParseResult
import com.github.javaparser.ParserConfiguration
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.Modifier
import com.github.javaparser.ast.Node
import com.github.javaparser.ast.NodeList
import com.github.javaparser.ast.body.*
import com.github.javaparser.ast.expr.*
import com.github.javaparser.ast.stmt.*
import com.github.javaparser.ast.type.ClassOrInterfaceType
import com.github.javaparser.ast.type.PrimitiveType
import com.github.javaparser.ast.type.Type
import java.io.File

/**
 * Post-processor that transforms decompiled Java source into stub code.
 *
 * This processor handles:
 * - Method bodies: Replace with `throw new GeneratedStubException();`
 * - Constructors: Keep super()/this() call, add `throw new GeneratedStubException();`
 * - Static initializers: Replace with default value assignments for static final fields
 * - Enum declarations:
 *   - Remove constructors (Java auto-generates default no-arg constructor)
 *   - Remove instance fields
 *   - Remove arguments from enum constant declarations
 *   - Remove anonymous class bodies from enum constants
 * - Abstract methods: Remove abstract modifier, add throw body
 * - Switch sanitization: Removes problematic Java 21 switch expressions/patterns before parsing
 */
class StubPostProcessor(
    private val parser: JavaParser,
) {
    companion object {
        private const val STUB_EXCEPTION = "io.github.hytalekt.stubs.GeneratedStubException"
        private const val STUB_EXCEPTION_SIMPLE = "GeneratedStubException"
    }

    init {
        // don't ask bro, gradle is weird as hell
        parser.parserConfiguration.languageLevel =
            ParserConfiguration.LanguageLevel::class.java
                .getDeclaredField("JAVA_25")
                .get(null) as ParserConfiguration.LanguageLevel
    }

    fun <T> ParseResult<T>.ok(): T {
        if (!this.isSuccessful) {
            throw ParseProblemException(this.problems)
        }
        return this.result.get()
    }

    /**
     * Process all Java files in a directory recursively.
     */
    fun processDirectory(directory: File) {
        directory
            .walkTopDown()
            .filter { it.isFile && it.extension == "java" }
            .forEach { file ->
                try {
                    val content = file.readText()
                    val processed = process(content, file.name)
                    if (processed != content) {
                        file.writeText(processed)
                    }
                } catch (e: Exception) {
                    System.err.println("StubPostProcessor: Failed to process ${file.name}: ${e.message}")
                }
            }
    }

    /**
     * Process a Java source file.
     *
     * @param content The Java source code content
     * @param filename Optional filename for error logging
     * @return The modified source code, or the original if parsing failed
     */
    fun process(
        content: String,
        filename: String? = null,
    ): String =
        try {
            // Pre-process to remove problematic decompiler output
            val sanitized = sanitizeDecompilerOutput(content)
            val cu = parser.parse(sanitized).ok()
            val modified = processCompilationUnit(cu)
            if (modified) {
                cu.toString()
            } else {
                sanitized
            }
        } catch (e: Exception) {
            System.err.println("StubPostProcessor: Failed to parse ${filename ?: "unknown file"}: ${e.message}")
            content
        }

    private fun processCompilationUnit(cu: CompilationUnit): Boolean {
        var modified = false
        var needsStubExceptionImport = false

        // Collect all type declarations
        val allEnums = mutableListOf<EnumDeclaration>()
        val allClasses = mutableListOf<ClassOrInterfaceDeclaration>()
        collectTypes(cu, allEnums, allClasses)

        // Process enums
        allEnums.forEach { enumDecl ->
            val result = processEnum(enumDecl)
            if (result.modified) modified = true
            if (result.needsImport) needsStubExceptionImport = true
        }

        // Process classes/interfaces
        allClasses.forEach { classDecl ->
            val result = processClassOrInterface(classDecl)
            if (result.modified) modified = true
            if (result.needsImport) needsStubExceptionImport = true
        }

        // Add import for GeneratedStubException if needed
        if (needsStubExceptionImport) {
            val hasImport = cu.imports.any { it.nameAsString == STUB_EXCEPTION }
            if (!hasImport) {
                cu.addImport(STUB_EXCEPTION)
            }
        }

        return modified
    }

    private fun collectTypes(
        node: Node,
        enums: MutableList<EnumDeclaration>,
        classes: MutableList<ClassOrInterfaceDeclaration>,
    ) {
        when (node) {
            is EnumDeclaration -> enums.add(node)
            is ClassOrInterfaceDeclaration -> classes.add(node)
        }
        for (child in node.childNodes) {
            collectTypes(child, enums, classes)
        }
    }

    private data class ProcessResult(
        val modified: Boolean,
        val needsImport: Boolean,
    )

    // ==================== Enum Processing ====================

    private fun processEnum(enumDecl: EnumDeclaration): ProcessResult {
        var modified = false
        var needsImport = false

        // Remove all constructors
        val constructors = enumDecl.constructors.toList()
        if (constructors.isNotEmpty()) {
            constructors.forEach { it.remove() }
            modified = true
        }

        // Remove instance fields (non-static)
        val instanceFields = enumDecl.fields.filter { !it.isStatic }.toList()
        if (instanceFields.isNotEmpty()) {
            instanceFields.forEach { it.remove() }
            modified = true
        }

        // Remove arguments and class bodies from enum constants
        enumDecl.entries.forEach { entry ->
            if (entry.arguments.isNotEmpty()) {
                entry.arguments.clear()
                modified = true
            }
            if (entry.classBody.isNotEmpty()) {
                entry.classBody.clear()
                modified = true
            }
        }

        // Convert abstract methods to concrete with throw
        enumDecl.methods.filter { it.isAbstract }.forEach { method ->
            method.modifiers.removeIf { it.keyword == Modifier.Keyword.ABSTRACT }
            method.setBody(createThrowBody())
            modified = true
            needsImport = true
        }

        // Process regular (non-abstract) methods with bodies
        enumDecl.methods.filter { !it.isAbstract && it.body.isPresent }.forEach { method ->
            method.setBody(createThrowBody())
            modified = true
            needsImport = true
        }

        // Replace ALL field initializers with default values (for legal stub generation)
        enumDecl.fields.forEach { field ->
            field.variables.forEach { variable ->
                variable.setInitializer(createDefaultValue(variable.type))
                modified = true
            }
        }

        // Remove static initializers (fields now have inline initializers)
        val staticInitializers = enumDecl.members.filterIsInstance<InitializerDeclaration>().filter { it.isStatic }
        staticInitializers.forEach { init ->
            init.remove()
            modified = true
        }

        return ProcessResult(modified, needsImport)
    }

    // ==================== Class/Interface Processing ====================

    private fun processClassOrInterface(classDecl: ClassOrInterfaceDeclaration): ProcessResult {
        var modified = false
        var needsImport = false

        // Handle interfaces
        if (classDecl.isInterface) {
            // Process default methods in interfaces
            classDecl.methods.filter { it.isDefault && it.body.isPresent }.forEach { method ->
                method.setBody(createThrowBody())
                modified = true
                needsImport = true
            }

            // Replace interface field initializers with default values (they are implicitly public static final)
            classDecl.fields.forEach { field ->
                field.variables.forEach { variable ->
                    variable.setInitializer(createDefaultValue(variable.type))
                    modified = true
                }
            }

            // Remove any static initializers from interfaces (shouldn't exist but decompiler may add them)
            val staticInitializers = classDecl.members.filterIsInstance<InitializerDeclaration>().filter { it.isStatic }
            if (staticInitializers.isNotEmpty()) {
                staticInitializers.forEach { it.remove() }
                modified = true
            }

            return ProcessResult(modified, needsImport)
        }

        // Process constructors
        classDecl.constructors.forEach { constructor ->
            val result = processConstructor(constructor)
            if (result.modified) modified = true
            if (result.needsImport) needsImport = true
        }

        // Process methods
        classDecl.methods.forEach { method ->
            val result = processMethod(method)
            if (result.modified) modified = true
            if (result.needsImport) needsImport = true
        }

        // Replace ALL field initializers with default values (for legal stub generation)
        classDecl.fields.forEach { field ->
            field.variables.forEach { variable ->
                variable.setInitializer(createDefaultValue(variable.type))
                modified = true
            }
        }

        // Remove static initializers (fields now have inline default values)
        val staticInitializers = classDecl.members.filterIsInstance<InitializerDeclaration>().filter { it.isStatic }
        staticInitializers.forEach { init ->
            init.remove()
            modified = true
        }

        // Remove instance initializers
        val instanceInitializers = classDecl.members.filterIsInstance<InitializerDeclaration>().filter { !it.isStatic }
        instanceInitializers.forEach { init ->
            init.remove()
            modified = true
        }

        return ProcessResult(modified, needsImport)
    }

    private fun processConstructor(constructor: ConstructorDeclaration): ProcessResult {
        val body = constructor.body
        val statements = body.statements

        // Find super() or this() call if present
        val delegateCall =
            statements.firstOrNull { it is ExplicitConstructorInvocationStmt }
                as? ExplicitConstructorInvocationStmt

        // Create new body with just delegate call (if any) + throw
        val newBody = BlockStmt()
        if (delegateCall != null) {
            // Replace delegate call arguments with default values and fix invalid calls
            val sanitizedCall = sanitizeDelegateCall(delegateCall, constructor)
            newBody.addStatement(sanitizedCall)
        }
        newBody.addStatement(createThrowStatement())

        constructor.setBody(newBody)
        return ProcessResult(modified = true, needsImport = true)
    }

    /**
     * Sanitize delegate calls (this() or super()) by:
     * 1. Replacing all arguments with cast expressions
     * 2. Fixing invalid this() calls that should be super() calls
     */
    private fun sanitizeDelegateCall(
        call: ExplicitConstructorInvocationStmt,
        constructor: ConstructorDeclaration,
    ): ExplicitConstructorInvocationStmt {
        // Get the containing class
        val containingClass =
            constructor.parentNode.orElse(null) as? ClassOrInterfaceDeclaration

        // Determine if this should be a this() or super() call
        val isThis =
            if (call.isThis && containingClass != null) {
                // Check if there's a matching constructor in this class
                val argCount = call.arguments.size
                containingClass.constructors.any { other ->
                    other != constructor && other.parameters.size == argCount
                }
            } else {
                call.isThis
            }

        // Find the target constructor to get parameter types
        val targetParams = findTargetConstructorParams(call, constructor, containingClass, isThis)

        // Replace all arguments with casted new Object()
        val defaultArgs = NodeList<Expression>()
        if (targetParams != null) {
            targetParams.forEach { param ->
                defaultArgs.add(createCastedObject(param.type))
            }
        } else {
            // Fallback: try to infer types from original arguments and cast
            call.arguments.forEach { arg ->
                defaultArgs.add(createCastedObjectFromExpr(arg))
            }
        }

        return ExplicitConstructorInvocationStmt(null, isThis, null, defaultArgs)
    }

    /**
     * Find the parameter list of the target constructor being called.
     */
    private fun findTargetConstructorParams(
        call: ExplicitConstructorInvocationStmt,
        constructor: ConstructorDeclaration,
        containingClass: ClassOrInterfaceDeclaration?,
        isThis: Boolean,
    ): NodeList<Parameter>? {
        val argCount = call.arguments.size

        if (isThis && containingClass != null) {
            // Find matching constructor in this class
            return containingClass.constructors
                .firstOrNull { other -> other != constructor && other.parameters.size == argCount }
                ?.parameters
        }

        // For super() calls, we can't easily find the parent constructor
        // Return null to use fallback
        return null
    }

    /**
     * Create a casted new Object() expression for the given type.
     * For primitives, uses double-cast through wrapper: (int)(Integer) new Object()
     * For reference types, uses: (Type) new Object()
     */
    private fun createCastedObject(type: Type): Expression {
        val newObject = ObjectCreationExpr(null, ClassOrInterfaceType(null, "Object"), NodeList())

        return if (type is PrimitiveType) {
            // For primitives: (primitive)(Wrapper) new Object()
            val wrapperType =
                when (type.type) {
                    PrimitiveType.Primitive.BOOLEAN -> "Boolean"
                    PrimitiveType.Primitive.BYTE -> "Byte"
                    PrimitiveType.Primitive.CHAR -> "Character"
                    PrimitiveType.Primitive.SHORT -> "Short"
                    PrimitiveType.Primitive.INT -> "Integer"
                    PrimitiveType.Primitive.LONG -> "Long"
                    PrimitiveType.Primitive.FLOAT -> "Float"
                    PrimitiveType.Primitive.DOUBLE -> "Double"
                }
            val wrapperCast = CastExpr(ClassOrInterfaceType(null, wrapperType), newObject)
            CastExpr(type, wrapperCast)
        } else {
            // For reference types: (Type) new Object()
            CastExpr(type, newObject)
        }
    }

    /**
     * Create a casted new Object() by inferring the type from an expression.
     * Used as fallback when we can't find the target constructor's parameter types.
     */
    private fun createCastedObjectFromExpr(expr: Expression): Expression {
        val newObject = ObjectCreationExpr(null, ClassOrInterfaceType(null, "Object"), NodeList())

        // Try to infer the type from the expression
        return when (expr) {
            is CastExpr -> {
                createCastedObject(expr.type)
            }

            is StringLiteralExpr -> {
                StringLiteralExpr("")
            }

            is CharLiteralExpr -> {
                CharLiteralExpr(' ')
            }

            is BooleanLiteralExpr -> {
                BooleanLiteralExpr(false)
            }

            is IntegerLiteralExpr -> {
                IntegerLiteralExpr("0")
            }

            is LongLiteralExpr -> {
                LongLiteralExpr("0L")
            }

            is DoubleLiteralExpr -> {
                DoubleLiteralExpr("0.0")
            }

            is ObjectCreationExpr,
            is NullLiteralExpr,
            -> {
                NullLiteralExpr()
            }

            is ArrayCreationExpr -> {
                CastExpr(ClassOrInterfaceType(null, "Object"), newObject)
            }

            else -> {
                // Default: cast to Object (will compile, may need manual fix)
                CastExpr(ClassOrInterfaceType(null, "Object"), newObject)
            }
        }
    }

    private fun processMethod(method: MethodDeclaration): ProcessResult {
        // Skip abstract methods (no body)
        if (method.isAbstract) {
            return ProcessResult(modified = false, needsImport = false)
        }

        // Skip native methods (no body)
        if (method.isNative) {
            return ProcessResult(modified = false, needsImport = false)
        }

        // Skip methods without bodies (e.g., interface methods)
        if (!method.body.isPresent) {
            return ProcessResult(modified = false, needsImport = false)
        }

        // Replace body with throw
        method.setBody(createThrowBody())
        return ProcessResult(modified = true, needsImport = true)
    }

    // ==================== Helper Methods ====================

    private fun createThrowBody(): BlockStmt {
        val body = BlockStmt()
        body.addStatement(createThrowStatement())
        return body
    }

    private fun createThrowStatement(): ThrowStmt {
        val exceptionType = ClassOrInterfaceType(null, STUB_EXCEPTION_SIMPLE)
        val newException = ObjectCreationExpr(null, exceptionType, NodeList())
        return ThrowStmt(newException)
    }

    private fun createStaticInitializerBody(staticFields: List<FieldDeclaration>): BlockStmt {
        val body = BlockStmt()

        staticFields.forEach { field ->
            field.variables.forEach { variable ->
                val fieldName = variable.nameAsString
                val fieldType = variable.type

                // Create: FieldName = defaultValue;
                val assignment =
                    AssignExpr(
                        NameExpr(fieldName),
                        createDefaultValue(fieldType),
                        AssignExpr.Operator.ASSIGN,
                    )
                body.addStatement(ExpressionStmt(assignment))
            }
        }

        return body
    }

    private fun createDefaultValue(type: Type): Expression =
        when {
            type is PrimitiveType -> {
                when (type.type) {
                    PrimitiveType.Primitive.BOOLEAN -> BooleanLiteralExpr(false)

                    PrimitiveType.Primitive.CHAR -> CharLiteralExpr('\u0000')

                    PrimitiveType.Primitive.BYTE,
                    PrimitiveType.Primitive.SHORT,
                    PrimitiveType.Primitive.INT,
                    -> IntegerLiteralExpr("0")

                    PrimitiveType.Primitive.LONG -> LongLiteralExpr("0L")

                    PrimitiveType.Primitive.FLOAT -> DoubleLiteralExpr("0.0f")

                    PrimitiveType.Primitive.DOUBLE -> DoubleLiteralExpr("0.0")
                }
            }

            else -> {
                NullLiteralExpr()
            }
        }

    // ==================== Switch Sanitization ====================

    /**
     * Sanitize problematic decompiler output that JavaParser cannot handle.
     *
     * This handles:
     * - Switch expressions (= switch (...) { ... })
     * - Type pattern matching in switch cases (case Type var ->)
     * - Null cases (case null ->)
     * - Labeled switches with break label
     * - SwitchBootstraps.typeSwitch<...>(...) low-level calls from Vineflower
     * - <unrepresentable>.$assertionsDisabled from Vineflower
     * - Static blocks in interfaces
     */
    private fun sanitizeDecompilerOutput(content: String): String {
        var result = content

        // Replace <unrepresentable>.$assertionsDisabled with true (assertions disabled)
        result = result.replace("<unrepresentable>.\$assertionsDisabled", "true")

        // Remove static blocks from interfaces (they can contain invalid decompiler output)
        result = removeInterfaceStaticBlocks(result)

        // Replace SwitchBootstraps.typeSwitch<...>(...) with just 0
        // This is Vineflower's fallback output for pattern matching switches
        result = replaceSwitchBootstraps(result)

        // Replace switch expressions (e.g., "= switch (x) { ... }" or "return (Type)(switch (x) { ... })")
        result = replaceSwitchExpressions(result)

        // Replace labeled switches (e.g., "label123: switch (...)")
        result = replaceLabeledSwitches(result)

        return result
    }

    /**
     * Remove static blocks from interfaces.
     * Interfaces shouldn't have static initializer blocks with complex logic.
     */
    private fun removeInterfaceStaticBlocks(content: String): String {
        var result = content

        // Find all interface declarations and remove their static blocks
        // Pattern: interface Name ... { ... static { ... } ... }
        val interfacePattern = Regex("""\binterface\s+\w+""")
        var match = interfacePattern.find(result)

        while (match != null) {
            // Find the opening brace of the interface
            val interfaceStart = match.range.first
            val openBrace = result.indexOf('{', match.range.last)
            if (openBrace == -1) {
                match = interfacePattern.find(result, match.range.last + 1)
                continue
            }

            // Find the closing brace of the interface
            val closeBrace = findMatchingBrace(result, openBrace)
            if (closeBrace == -1) {
                match = interfacePattern.find(result, match.range.last + 1)
                continue
            }

            // Search for static blocks within this interface
            val interfaceBody = result.substring(openBrace, closeBrace + 1)
            val staticBlockPattern = Regex("""\bstatic\s*\{""")
            val staticMatch = staticBlockPattern.find(interfaceBody)

            if (staticMatch != null) {
                val absoluteStaticStart = openBrace + staticMatch.range.first
                val staticOpenBrace = result.indexOf('{', absoluteStaticStart)
                if (staticOpenBrace != -1) {
                    val staticCloseBrace = findMatchingBrace(result, staticOpenBrace)
                    if (staticCloseBrace != -1) {
                        // Remove the static block entirely
                        result =
                            result.substring(0, absoluteStaticStart) +
                            result.substring(staticCloseBrace + 1)
                        // Continue searching from the same position (content shifted)
                        match = interfacePattern.find(result, interfaceStart)
                        continue
                    }
                }
            }

            match = interfacePattern.find(result, closeBrace + 1)
        }

        return result
    }

    /**
     * Replace SwitchBootstraps.typeSwitch<...>(...) calls with just 0.
     * Vineflower outputs these when pattern matching is disabled but the bytecode uses it.
     */
    private fun replaceSwitchBootstraps(content: String): String {
        val result = StringBuilder()
        var i = 0
        val pattern = "SwitchBootstraps.typeSwitch<"

        while (i < content.length) {
            val idx = content.indexOf(pattern, i)
            if (idx == -1) {
                result.append(content.substring(i))
                break
            }

            // Append everything before this match
            result.append(content.substring(i, idx))

            // Find the matching > for the type parameters
            val angleStart = idx + pattern.length - 1 // index of '<'
            var angleDepth = 1
            var j = angleStart + 1
            while (j < content.length && angleDepth > 0) {
                when (content[j]) {
                    '<' -> angleDepth++
                    '>' -> angleDepth--
                }
                j++
            }

            if (angleDepth != 0) {
                // Couldn't find matching >, just append the pattern and continue
                result.append(pattern)
                i = idx + pattern.length
                continue
            }

            // j is now just after the closing >
            // Now find the matching ) for the call arguments
            val openParen = content.indexOf('(', j)
            if (openParen == -1 || openParen > j + 5) {
                // No ( found nearby, just skip this
                result.append("0")
                i = j
                continue
            }

            var parenDepth = 1
            var k = openParen + 1
            while (k < content.length && parenDepth > 0) {
                when (content[k]) {
                    '(' -> parenDepth++
                    ')' -> parenDepth--
                }
                k++
            }

            // Replace the entire SwitchBootstraps.typeSwitch<...>(...) with 0
            result.append("0")
            i = k
        }

        return result.toString()
    }

    /**
     * Replace switch expressions with null.
     * Handles patterns like:
     * - variable = switch (x) { ... };
     * - return switch (x) { ... };
     * - return (Type)(switch (x) { ... });
     */
    private fun replaceSwitchExpressions(content: String): String {
        val result = StringBuilder()
        var i = 0

        while (i < content.length) {
            // Look for switch expression patterns
            val switchExprStart = findSwitchExpressionStart(content, i)
            if (switchExprStart == -1) {
                result.append(content.substring(i))
                break
            }

            // Append everything before the switch expression
            result.append(content.substring(i, switchExprStart))

            // Find the switch keyword position
            val switchKeyword = content.indexOf("switch", switchExprStart)
            if (switchKeyword == -1) {
                result.append(content.substring(switchExprStart))
                break
            }

            // Find opening brace of switch body
            val openBrace = content.indexOf('{', switchKeyword)
            if (openBrace == -1) {
                result.append(content.substring(switchExprStart))
                break
            }

            // Find matching closing brace
            val closeBrace = findMatchingBrace(content, openBrace)
            if (closeBrace == -1) {
                result.append(content.substring(switchExprStart))
                break
            }

            // Replace the switch expression with null
            result.append("null")
            i = closeBrace + 1
        }

        return result.toString()
    }

    /**
     * Find the start of a switch expression (where it's used as a value).
     */
    private fun findSwitchExpressionStart(
        content: String,
        start: Int,
    ): Int {
        // Look for patterns that indicate switch is used as an expression:
        // "= switch", "return switch", "(switch", "return (Type)(switch"
        val patterns =
            listOf(
                Regex("""=\s*switch\s*\("""),
                Regex("""return\s+switch\s*\("""),
                Regex("""return\s+\([^)]+\)\s*\(\s*switch\s*\("""),
                Regex("""\(\s*switch\s*\("""),
            )

        var earliest = -1
        for (pattern in patterns) {
            val match = pattern.find(content, start)
            if (match != null) {
                if (earliest == -1 || match.range.first < earliest) {
                    earliest = match.range.first
                }
            }
        }

        return earliest
    }

    /**
     * Replace labeled switches and switches with problematic case patterns.
     */
    private fun replaceLabeledSwitches(content: String): String {
        var result = content

        // Replace labeled switches: "label123: switch (...) { ... }" -> remove the whole construct
        val labeledSwitchPattern = Regex("""(\w+):\s*switch\s*\([^)]*\)\s*\{""")
        var match = labeledSwitchPattern.find(result)

        while (match != null) {
            val openBrace = result.indexOf('{', match.range.first)
            if (openBrace != -1) {
                val closeBrace = findMatchingBrace(result, openBrace)
                if (closeBrace != -1) {
                    // Replace the labeled switch with an empty block
                    result =
                        result.substring(0, match.range.first) +
                        "/* sanitized switch */ {}" +
                        result.substring(closeBrace + 1)
                    match = labeledSwitchPattern.find(result)
                    continue
                }
            }
            break
        }

        // Replace switches with "case null" pattern
        result = replaceSwitchesWithNullCase(result)

        return result
    }

    /**
     * Replace switch statements that contain "case null" with an empty block.
     */
    private fun replaceSwitchesWithNullCase(content: String): String {
        var result = content
        var searchStart = 0

        while (true) {
            // Find next switch statement
            val switchIdx = result.indexOf("switch", searchStart)
            if (switchIdx == -1) break

            // Make sure it's not part of another word
            if (switchIdx > 0 && result[switchIdx - 1].isLetterOrDigit()) {
                searchStart = switchIdx + 1
                continue
            }

            // Find the opening brace
            val openBrace = result.indexOf('{', switchIdx)
            if (openBrace == -1) break

            // Find the closing brace
            val closeBrace = findMatchingBrace(result, openBrace)
            if (closeBrace == -1) break

            // Check if this switch contains "case null"
            val switchBody = result.substring(openBrace, closeBrace + 1)
            if (switchBody.contains(Regex("""\bcase\s+null\b"""))) {
                // Find the start of this statement (could be after 'return' or an assignment)
                var stmtStart = switchIdx
                val beforeSwitch = result.substring(maxOf(0, switchIdx - 50), switchIdx)

                // Check if it's a return switch
                val returnMatch = Regex("""return\s*$""").find(beforeSwitch)
                if (returnMatch != null) {
                    stmtStart = switchIdx - (beforeSwitch.length - returnMatch.range.first)
                    // Replace with "return null"
                    result =
                        result.substring(0, stmtStart) +
                        "return null" +
                        result.substring(closeBrace + 1)
                    continue
                }

                // Check if it's an assignment switch
                val assignMatch = Regex("""=\s*$""").find(beforeSwitch)
                if (assignMatch != null) {
                    // Replace just the switch with null
                    result =
                        result.substring(0, switchIdx) +
                        "null" +
                        result.substring(closeBrace + 1)
                    continue
                }

                // Regular switch statement - replace body with empty
                result =
                    result.substring(0, openBrace) +
                    "{ /* sanitized */ }" +
                    result.substring(closeBrace + 1)
            } else {
                searchStart = closeBrace + 1
            }
        }

        return result
    }

    /**
     * Find the matching closing brace for an opening brace.
     */
    private fun findMatchingBrace(
        content: String,
        openBraceIndex: Int,
    ): Int {
        if (openBraceIndex >= content.length || content[openBraceIndex] != '{') {
            return -1
        }

        var depth = 1
        var i = openBraceIndex + 1
        var inString = false
        var inChar = false
        var escaped = false

        while (i < content.length && depth > 0) {
            val c = content[i]

            if (escaped) {
                escaped = false
                i++
                continue
            }

            if (c == '\\') {
                escaped = true
                i++
                continue
            }

            if (c == '"' && !inChar) {
                inString = !inString
            } else if (c == '\'' && !inString) {
                inChar = !inChar
            } else if (!inString && !inChar) {
                when (c) {
                    '{' -> depth++
                    '}' -> depth--
                }
            }

            i++
        }

        return if (depth == 0) i - 1 else -1
    }
}
