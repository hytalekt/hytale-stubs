package io.github.hytalekt.stubs.vineflower

import com.github.javaparser.ParserConfiguration
import com.github.javaparser.StaticJavaParser
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.Modifier
import com.github.javaparser.ast.Node
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.body.ConstructorDeclaration
import com.github.javaparser.ast.body.EnumDeclaration
import com.github.javaparser.ast.body.TypeDeclaration
import com.github.javaparser.ast.expr.MethodCallExpr
import com.github.javaparser.ast.expr.ObjectCreationExpr
import com.github.javaparser.ast.expr.SuperExpr
import com.github.javaparser.ast.stmt.BlockStmt
import com.github.javaparser.ast.stmt.ExplicitConstructorInvocationStmt
import com.github.javaparser.ast.stmt.ThrowStmt
import com.github.javaparser.ast.type.ClassOrInterfaceType
import java.io.File

/**
 * Post-processor that modifies generated Java source files.
 *
 * This processor handles:
 * - Enum declarations:
 *   - Removes all constructors (Java will auto-generate default no-arg constructor)
 *   - Removes instance fields (non-static fields used by constructors)
 *   - Removes arguments from enum constant declarations
 * - Abstract methods:
 *   - Removes the abstract modifier
 *   - Adds a body with `throw new GeneratedStubException();`
 *
 * This is necessary because Vineflower's plugin API operates at the method level,
 * and cannot directly modify class structure or abstract method declarations.
 */
object EnumPostProcessor {

    private const val STUB_EXCEPTION = "io.github.hytalekt.stubs.GeneratedStubException"

    init {
        // Configure JavaParser to use Java 17 to support records and sealed classes
        StaticJavaParser.setConfiguration(
            ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17)
        )
    }

    /**
     * Process a Java source file, modifying declarations as needed.
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
            // If parsing fails, log and return original content
            System.err.println("EnumPostProcessor: Failed to parse ${filename ?: "unknown file"}: ${e.message}")
            content
        }
    }

    /**
     * Process all Java files in a directory recursively.
     *
     * @param directory The directory containing Java source files
     */
    fun processDirectory(directory: File) {
        directory.walkTopDown()
            .filter { it.isFile && it.extension == "java" }
            .forEach { file ->
                val content = file.readText()
                val processed = process(content, file.name)
                if (processed != content) {
                    println("EnumPostProcessor: Modified ${file.name}")
                    file.writeText(processed)
                }
            }
    }

    /**
     * Process a compilation unit, returning whether any modifications were made.
     */
    private fun processCompilationUnit(cu: CompilationUnit): Boolean {
        var modified = false
        var needsStubExceptionImport = false

        // Find all enum declarations using walk() to ensure we get nested enums
        val allEnums = mutableListOf<EnumDeclaration>()
        collectEnums(cu, allEnums)

        if (allEnums.isNotEmpty()) {
            println("EnumPostProcessor: Found ${allEnums.size} enum(s): ${allEnums.map { it.nameAsString }}")
        }

        // Process all found enums
        allEnums.forEach { enumDecl ->
            val result = processEnumDeclaration(enumDecl)
            if (result.modified) {
                println("EnumPostProcessor: Modified enum ${enumDecl.nameAsString}")
                modified = true
            }
            if (result.needsImport) {
                needsStubExceptionImport = true
            }
        }

        // Fix invalid this() calls in constructors (Vineflower sometimes decompiles super() as this())
        val allClasses = mutableListOf<ClassOrInterfaceDeclaration>()
        collectClasses(cu, allClasses)

        allClasses.forEach { classDecl ->
            if (fixInvalidThisCalls(classDecl)) {
                modified = true
            }
        }

        // Add import for GeneratedStubException if needed and not already present
        if (needsStubExceptionImport) {
            val hasImport = cu.imports.any { it.nameAsString == STUB_EXCEPTION }
            if (!hasImport) {
                cu.addImport(STUB_EXCEPTION)
            }
        }

        return modified
    }

    /**
     * Recursively collect all EnumDeclaration nodes from the AST.
     */
    private fun collectEnums(node: Node, enums: MutableList<EnumDeclaration>) {
        if (node is EnumDeclaration) {
            enums.add(node)
        }
        for (child in node.childNodes) {
            collectEnums(child, enums)
        }
    }

    /**
     * Recursively collect all ClassOrInterfaceDeclaration nodes from the AST.
     */
    private fun collectClasses(node: Node, classes: MutableList<ClassOrInterfaceDeclaration>) {
        if (node is ClassOrInterfaceDeclaration && !node.isInterface) {
            classes.add(node)
        }
        for (child in node.childNodes) {
            collectClasses(child, classes)
        }
    }

    /**
     * Fix invalid this() calls in constructors.
     * Vineflower sometimes incorrectly decompiles super() calls as this() calls.
     * This detects this() calls where no matching constructor exists and replaces them with super().
     */
    private fun fixInvalidThisCalls(classDecl: ClassOrInterfaceDeclaration): Boolean {
        var modified = false
        val constructors = classDecl.constructors

        for (constructor in constructors) {
            val body = constructor.body
            if (body.statements.isEmpty()) continue

            val firstStmt = body.statements.first()
            if (firstStmt !is ExplicitConstructorInvocationStmt) continue

            // Check if it's a this() call (not super())
            if (firstStmt.isThis) {
                val argCount = firstStmt.arguments.size

                // Check if there's another constructor in this class with matching parameter count
                val hasMatchingConstructor = constructors.any { other ->
                    other != constructor && other.parameters.size == argCount
                }

                // If no matching constructor exists, this is likely a misidentified super() call
                if (!hasMatchingConstructor) {
                    // Create a new super() call with the same arguments
                    // Constructor: ExplicitConstructorInvocationStmt(typeArguments, isThis, expression, arguments)
                    val superCall = ExplicitConstructorInvocationStmt(
                        null,  // no type arguments
                        false, // isThis = false means super()
                        null,  // no expression
                        firstStmt.arguments // keep the same arguments
                    )

                    // Replace the this() call with super()
                    body.statements[0] = superCall
                    println("EnumPostProcessor: Fixed invalid this() -> super() in ${classDecl.nameAsString}.${constructor.nameAsString}")
                    modified = true
                }
            }
        }

        return modified
    }

    private data class ProcessResult(val modified: Boolean, val needsImport: Boolean)

    /**
     * Process a single enum declaration.
     */
    private fun processEnumDeclaration(enumDecl: EnumDeclaration): ProcessResult {
        var modified = false
        var needsImport = false

        // Remove all constructors
        val constructors = enumDecl.constructors.toList()
        if (constructors.isNotEmpty()) {
            println("EnumPostProcessor: Removing ${constructors.size} constructor(s) from ${enumDecl.nameAsString}")
            constructors.forEach { it.remove() }
            modified = true
        }

        // Remove instance fields (non-static fields)
        val instanceFields = enumDecl.fields.filter { !it.isStatic }.toList()
        if (instanceFields.isNotEmpty()) {
            println("EnumPostProcessor: Removing ${instanceFields.size} instance field(s) from ${enumDecl.nameAsString}: ${instanceFields.map { it.variables.joinToString { v -> v.nameAsString } }}")
            instanceFields.forEach { it.remove() }
            modified = true
        }

        // Remove arguments from enum constants
        enumDecl.entries.forEach { entry ->
            if (entry.arguments.isNotEmpty()) {
                entry.arguments.clear()
                modified = true
            }
            // Also remove class body from enum constants (anonymous inner class implementations)
            if (entry.classBody.isNotEmpty()) {
                entry.classBody.clear()
                modified = true
            }
        }

        // Convert abstract methods in enums to concrete with throw statement
        // (Enums can have abstract methods when they have per-constant implementations)
        enumDecl.methods.filter { it.isAbstract }.forEach { method ->
            // Remove the abstract modifier
            method.modifiers.removeIf { it.keyword == Modifier.Keyword.ABSTRACT }

            // Create: throw new GeneratedStubException();
            val exceptionType = ClassOrInterfaceType(null, "GeneratedStubException")
            val newException = ObjectCreationExpr(null, exceptionType, com.github.javaparser.ast.NodeList())
            val throwStmt = ThrowStmt(newException)

            // Set the body
            val body = BlockStmt()
            body.addStatement(throwStmt)
            method.setBody(body)

            modified = true
            needsImport = true
        }

        return ProcessResult(modified, needsImport)
    }
}
