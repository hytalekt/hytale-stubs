package io.github.hytalekt.stubs.postprocess

import com.github.javaparser.ParserConfiguration
import com.github.javaparser.StaticJavaParser
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
 */
object StubPostProcessor {

    private const val STUB_EXCEPTION = "io.github.hytalekt.stubs.GeneratedStubException"
    private const val STUB_EXCEPTION_SIMPLE = "GeneratedStubException"

    init {
        StaticJavaParser.setConfiguration(
            ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17)
        )
    }

    /**
     * Process all Java files in a directory recursively.
     */
    fun processDirectory(directory: File) {
        directory.walkTopDown()
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
    fun process(content: String, filename: String? = null): String {
        return try {
            val cu = StaticJavaParser.parse(content)
            val modified = processCompilationUnit(cu)
            if (modified) {
                cu.toString()
            } else {
                content
            }
        } catch (e: Exception) {
            System.err.println("StubPostProcessor: Failed to parse ${filename ?: "unknown file"}: ${e.message}")
            content
        }
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
        classes: MutableList<ClassOrInterfaceDeclaration>
    ) {
        when (node) {
            is EnumDeclaration -> enums.add(node)
            is ClassOrInterfaceDeclaration -> classes.add(node)
        }
        for (child in node.childNodes) {
            collectTypes(child, enums, classes)
        }
    }

    private data class ProcessResult(val modified: Boolean, val needsImport: Boolean)

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

        // Process static initializers - replace with default assignments
        val staticInitializers = enumDecl.members.filterIsInstance<InitializerDeclaration>().filter { it.isStatic }
        staticInitializers.forEach { init ->
            val staticFields = enumDecl.fields.filter { it.isStatic && it.isFinal }
            init.body = createStaticInitializerBody(staticFields)
            modified = true
        }

        return ProcessResult(modified, needsImport)
    }

    // ==================== Class/Interface Processing ====================

    private fun processClassOrInterface(classDecl: ClassOrInterfaceDeclaration): ProcessResult {
        var modified = false
        var needsImport = false

        // Skip interfaces (no method bodies to process, unless default methods)
        if (classDecl.isInterface) {
            // Process default methods in interfaces
            classDecl.methods.filter { it.isDefault && it.body.isPresent }.forEach { method ->
                method.setBody(createThrowBody())
                modified = true
                needsImport = true
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

        // Process static initializers
        val staticInitializers = classDecl.members.filterIsInstance<InitializerDeclaration>().filter { it.isStatic }
        staticInitializers.forEach { init ->
            val staticFields = classDecl.fields.filter { it.isStatic && it.isFinal }
            init.body = createStaticInitializerBody(staticFields)
            modified = true
        }

        // Process instance initializers - just remove the body content
        val instanceInitializers = classDecl.members.filterIsInstance<InitializerDeclaration>().filter { !it.isStatic }
        instanceInitializers.forEach { init ->
            init.body = BlockStmt()
            modified = true
        }

        return ProcessResult(modified, needsImport)
    }

    private fun processConstructor(constructor: ConstructorDeclaration): ProcessResult {
        val body = constructor.body
        val statements = body.statements

        // Find super() or this() call if present
        val delegateCall = statements.firstOrNull { it is ExplicitConstructorInvocationStmt }
                as? ExplicitConstructorInvocationStmt

        // Create new body with just delegate call (if any) + throw
        val newBody = BlockStmt()
        if (delegateCall != null) {
            // Keep the delegate call but fix invalid this() calls
            val fixedCall = fixInvalidDelegateCall(delegateCall, constructor)
            newBody.addStatement(fixedCall)
        }
        newBody.addStatement(createThrowStatement())

        constructor.setBody(newBody)
        return ProcessResult(modified = true, needsImport = true)
    }

    /**
     * Fix invalid this() calls that should be super() calls.
     * Vineflower sometimes incorrectly decompiles super() as this().
     */
    private fun fixInvalidDelegateCall(
        call: ExplicitConstructorInvocationStmt,
        constructor: ConstructorDeclaration
    ): ExplicitConstructorInvocationStmt {
        if (!call.isThis) return call

        // Get the containing class
        val containingClass = constructor.parentNode.orElse(null) as? ClassOrInterfaceDeclaration
            ?: return call

        // Check if there's a matching constructor in this class
        val argCount = call.arguments.size
        val hasMatchingConstructor = containingClass.constructors.any { other ->
            other != constructor && other.parameters.size == argCount
        }

        // If no matching constructor, convert this() to super()
        return if (!hasMatchingConstructor) {
            ExplicitConstructorInvocationStmt(null, false, null, call.arguments)
        } else {
            call
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
                val assignment = AssignExpr(
                    NameExpr(fieldName),
                    createDefaultValue(fieldType),
                    AssignExpr.Operator.ASSIGN
                )
                body.addStatement(ExpressionStmt(assignment))
            }
        }

        return body
    }

    private fun createDefaultValue(type: Type): Expression {
        return when {
            type is PrimitiveType -> when (type.type) {
                PrimitiveType.Primitive.BOOLEAN -> BooleanLiteralExpr(false)
                PrimitiveType.Primitive.CHAR -> CharLiteralExpr('\u0000')
                PrimitiveType.Primitive.BYTE,
                PrimitiveType.Primitive.SHORT,
                PrimitiveType.Primitive.INT -> IntegerLiteralExpr("0")
                PrimitiveType.Primitive.LONG -> LongLiteralExpr("0L")
                PrimitiveType.Primitive.FLOAT -> DoubleLiteralExpr("0.0f")
                PrimitiveType.Primitive.DOUBLE -> DoubleLiteralExpr("0.0")
            }
            else -> NullLiteralExpr()
        }
    }
}
