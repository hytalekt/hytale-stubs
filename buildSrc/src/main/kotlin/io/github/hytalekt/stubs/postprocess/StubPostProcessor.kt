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
import com.github.javaparser.ast.type.ArrayType
import com.github.javaparser.ast.type.ClassOrInterfaceType
import com.github.javaparser.ast.type.PrimitiveType
import com.github.javaparser.ast.type.Type
import io.github.classgraph.TypeSignature
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
 * - Generic type resolution: Fixes erased types in super()/this() calls with full generic signatures
 */
class StubPostProcessor(
    private val parser: JavaParser,
    private val signatureIndex: ConstructorSignatureIndex? = null,
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

        // Get package name for fully qualified class name resolution
        val packageName = cu.packageDeclaration.map { it.nameAsString }.orElse("")

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
            val result = processClassOrInterface(classDecl, packageName, cu)
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

    private fun processClassOrInterface(
        classDecl: ClassOrInterfaceDeclaration,
        packageName: String,
        cu: CompilationUnit,
    ): ProcessResult {
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

        // Build context for constructor processing
        // Handle nested classes - build the JVM-style name with $ separators
        val className = classDecl.nameAsString
        val fullyQualifiedName = buildFullyQualifiedClassName(classDecl, packageName)
        val extendedType = classDecl.extendedTypes.firstOrNull()
        // Use nameWithScope to get full nested class path (e.g., "Evaluator.OptionHolder" not just "OptionHolder")
        val parentClass = extendedType?.nameWithScope
        // Extract type arguments from extends clause (e.g., Layer<DynamicLayerEntry> -> ["DynamicLayerEntry"])
        val parentTypeArgs = extendedType?.typeArguments?.orElse(null)?.map { it.asString() } ?: emptyList()

        // Process constructors
        classDecl.constructors.forEach { constructor ->
            val result = processConstructor(constructor, fullyQualifiedName, parentClass, parentTypeArgs, packageName, cu)
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

    private fun processConstructor(
        constructor: ConstructorDeclaration,
        fullyQualifiedClassName: String,
        parentClassName: String?,
        parentTypeArgs: List<String>,
        packageName: String,
        cu: CompilationUnit,
    ): ProcessResult {
        val body = constructor.body
        val statements = body.statements

        // Find super() or this() call if present
        // Vineflower's ConstructorStubPass already replaced arguments with default values
        var delegateCall =
            statements.firstOrNull { it is ExplicitConstructorInvocationStmt }
                as? ExplicitConstructorInvocationStmt

        // If no delegate call exists but class extends something, try to generate a super() call
        // This handles Vineflower decompilation failures
        if (delegateCall == null && parentClassName != null) {
            delegateCall = generateSuperCall(parentClassName, packageName, fullyQualifiedClassName, cu)
        }

        // Create new body with just delegate call (if any) + throw
        val newBody = BlockStmt()
        if (delegateCall != null) {
            // Fix generic types in the delegate call arguments
            fixGenericTypesInDelegateCall(delegateCall, fullyQualifiedClassName, parentClassName, parentTypeArgs, packageName, cu)
            // Strip private inner class references from generic type arguments
            stripPrivateClassReferences(delegateCall, cu)
            newBody.addStatement(delegateCall.clone())
        }
        newBody.addStatement(createThrowStatement())

        constructor.setBody(newBody)
        return ProcessResult(modified = true, needsImport = true)
    }

    /**
     * Generate a super() call when Vineflower failed to decompile the constructor.
     * Looks up the parent class constructors and creates a call with default arguments.
     */
    private fun generateSuperCall(
        parentClassName: String,
        packageName: String,
        fullyQualifiedClassName: String,
        cu: CompilationUnit,
    ): ExplicitConstructorInvocationStmt? {
        if (signatureIndex == null) return null

        val resolvedParent = resolveParentClassName(parentClassName, packageName, fullyQualifiedClassName, cu)
        val constructors = signatureIndex.getConstructors(resolvedParent) ?: return null

        // Find a constructor to call - prefer one with fewer parameters
        val (descriptor, paramTypes) =
            constructors.entries
                .minByOrNull { it.value.size }
                ?: return null

        // Generate default arguments for each parameter
        val args = NodeList<Expression>()
        for (paramSig in paramTypes) {
            val defaultValue = createDefaultValueForSignature(paramSig, packageName, cu)
            args.add(defaultValue)
        }

        return ExplicitConstructorInvocationStmt(false, null, args)
    }

    /**
     * Create a default value expression for a ClassGraph TypeSignature.
     */
    private fun createDefaultValueForSignature(
        sig: io.github.classgraph.TypeSignature,
        packageName: String,
        cu: CompilationUnit,
    ): Expression =
        when (sig) {
            is io.github.classgraph.BaseTypeSignature -> {
                when (sig.type) {
                    Boolean::class.javaPrimitiveType -> {
                        BooleanLiteralExpr(false)
                    }

                    Char::class.javaPrimitiveType -> {
                        CharLiteralExpr('\u0000')
                    }

                    Byte::class.javaPrimitiveType, Short::class.javaPrimitiveType, Int::class.javaPrimitiveType -> {
                        IntegerLiteralExpr("0")
                    }

                    Long::class.javaPrimitiveType -> {
                        LongLiteralExpr("0L")
                    }

                    Float::class.javaPrimitiveType -> {
                        DoubleLiteralExpr("0.0f")
                    }

                    Double::class.javaPrimitiveType -> {
                        DoubleLiteralExpr("0.0")
                    }

                    else -> {
                        IntegerLiteralExpr("0")
                    }
                }
            }

            else -> {
                // For reference types, cast null to the appropriate type
                val javaType = TypeSignatureRenderer.toJavaParserType(sig, emptyMap(), packageName, cu)
                CastExpr(javaType, NullLiteralExpr())
            }
        }

    /**
     * Strip type arguments that reference private inner classes.
     * This handles cases where Vineflower generates casts to generic types
     * with private inner class type arguments.
     */
    private fun stripPrivateClassReferences(
        delegateCall: ExplicitConstructorInvocationStmt,
        cu: CompilationUnit,
    ) {
        delegateCall.arguments.forEach { arg ->
            if (arg is CastExpr) {
                stripPrivateClassFromType(arg.type, cu)
            }
        }
    }

    /**
     * Recursively strip type arguments that might reference private classes.
     * When a type argument contains a reference to an inner class, remove all type arguments
     * to use the raw type instead.
     */
    private fun stripPrivateClassFromType(
        type: Type,
        cu: CompilationUnit,
    ) {
        if (type is ClassOrInterfaceType) {
            val typeArgs = type.typeArguments.orElse(null)
            if (typeArgs != null) {
                // Check if any type argument references an inner class (contains '.')
                // Inner classes in type arguments often cause private access issues
                val hasInnerClassRef =
                    typeArgs.any { typeArg ->
                        typeArg is ClassOrInterfaceType && typeArg.nameAsString.contains('.')
                    }
                if (hasInnerClassRef) {
                    type.removeTypeArguments()
                }
            }
        }
    }

    /**
     * Fix generic types in super()/this() call arguments.
     * The Vineflower pass generates casts using erased types from descriptors.
     * This method looks up the actual generic signatures and updates the cast types.
     *
     * @param parentTypeArgs Type arguments from the extends clause (e.g., ["DynamicLayerEntry"] for Layer<DynamicLayerEntry>)
     */
    private fun fixGenericTypesInDelegateCall(
        delegateCall: ExplicitConstructorInvocationStmt,
        fullyQualifiedClassName: String,
        parentClassName: String?,
        parentTypeArgs: List<String>,
        packageName: String,
        cu: CompilationUnit,
    ) {
        if (signatureIndex == null) return

        // Determine target class: this() calls target current class, super() calls parent
        val isThisCall = delegateCall.isThis

        // For this() calls in generic classes, we can't properly substitute type parameters
        // because we don't have concrete type arguments - skip fixing to avoid bound violations
        if (isThisCall) {
            val currentTypeParams = signatureIndex.getTypeParameters(fullyQualifiedClassName)
            if (!currentTypeParams.isNullOrEmpty()) {
                return // Skip fixing for this() calls in generic classes
            }
        }

        val targetClassName =
            if (isThisCall) {
                fullyQualifiedClassName
            } else {
                // For super(), we need to resolve the parent class name
                if (parentClassName != null) {
                    resolveParentClassName(parentClassName, packageName, fullyQualifiedClassName, cu)
                } else {
                    return // No parent class to resolve
                }
            }

        // Get all constructors for the target class
        val constructors = signatureIndex.getConstructors(targetClassName)
        if (constructors == null) {
            return
        }

        // Find matching constructor using fuzzy matching
        // Vineflower may generate Object for type parameters, so we can't rely on exact descriptors
        val genericSignatures = findMatchingConstructor(delegateCall.arguments, constructors, cu)

        if (genericSignatures == null) {
            return
        }

        // Get type parameter names from target class for substitution
        val typeParamNames = signatureIndex.getTypeParameters(targetClassName) ?: emptyList()

        // Update cast types in arguments
        delegateCall.arguments.forEachIndexed { index, arg ->
            if (index < genericSignatures.size) {
                updateCastType(arg, genericSignatures[index], typeParamNames, parentTypeArgs, packageName, cu)
            }
        }
    }

    /**
     * Find matching constructor using fuzzy matching.
     *
     * Vineflower may generate Object or Object[] for type parameters, so exact
     * descriptor matching won't work. Instead we:
     * 1. Try exact match first
     * 2. Match by parameter count
     * 3. If multiple matches, prefer the one with best type compatibility
     */
    private fun findMatchingConstructor(
        arguments: NodeList<Expression>,
        constructors: Map<String, List<TypeSignature>>,
        cu: CompilationUnit?,
    ): List<TypeSignature>? {
        val argCount = arguments.size

        // First try exact descriptor match
        val descriptor = buildDescriptorFromArguments(arguments, cu)
        constructors[descriptor]?.let { return it }

        // Filter by parameter count
        val byArity = constructors.filter { (_, params) -> params.size == argCount }

        if (byArity.isEmpty()) return null
        if (byArity.size == 1) return byArity.values.first()

        // Multiple matches - try to find best match by comparing argument types
        val argTypes = arguments.map { getArgumentType(it) }

        // Score each candidate
        val scored =
            byArity.map { (desc, params) ->
                val score =
                    argTypes.zip(params).sumOf { (argType, paramSig) ->
                        typeMatchScore(argType, paramSig)
                    }
                params to score
            }

        // Return the one with highest score
        return scored.maxByOrNull { it.second }?.first
    }

    /**
     * Score how well an argument type matches a parameter signature.
     * Higher score = better match.
     */
    private fun typeMatchScore(
        argType: Type?,
        paramSig: TypeSignature,
    ): Int {
        if (argType == null) return 0

        return when (paramSig) {
            is io.github.classgraph.BaseTypeSignature -> {
                // Primitive types - exact match required
                if (argType is PrimitiveType) {
                    val paramPrimitive = paramSig.typeStr
                    val argPrimitive = argType.type.name.lowercase()
                    if (paramPrimitive == argPrimitive) 10 else 0
                } else {
                    0
                }
            }

            is io.github.classgraph.ArrayTypeSignature -> {
                // Array types - check element type compatibility
                if (argType is ArrayType) {
                    // Both are arrays - bonus points
                    5 + typeMatchScore(argType.componentType, paramSig.elementTypeSignature)
                } else {
                    0
                }
            }

            is io.github.classgraph.ClassRefTypeSignature -> {
                // Class types - check name match
                if (argType is ClassOrInterfaceType) {
                    val argName = argType.nameAsString
                    val paramName = paramSig.fullyQualifiedClassName.substringAfterLast('.')
                    when {
                        argName == paramName -> 10

                        argName == "Object" -> 1

                        // Object matches anything but weakly
                        else -> 0
                    }
                } else {
                    0
                }
            }

            is io.github.classgraph.TypeVariableSignature -> {
                // Type variables match Object or the bound
                if (argType is ClassOrInterfaceType && argType.nameAsString == "Object") 1 else 0
            }

            else -> {
                0
            }
        }
    }

    /**
     * Build the fully qualified class name for a class declaration,
     * using $ for nested classes as the JVM does.
     */
    private fun buildFullyQualifiedClassName(
        classDecl: ClassOrInterfaceDeclaration,
        packageName: String,
    ): String {
        val nestedPath = mutableListOf(classDecl.nameAsString)

        // Walk up the parent chain to find enclosing classes
        var parent = classDecl.parentNode.orElse(null)
        while (parent != null) {
            when (parent) {
                is ClassOrInterfaceDeclaration -> nestedPath.add(0, parent.nameAsString)
                is EnumDeclaration -> nestedPath.add(0, parent.nameAsString)
            }
            parent = parent.parentNode.orElse(null)
        }

        // Build the name: package.OuterClass$InnerClass$...
        val classPath = nestedPath.joinToString("$")
        return if (packageName.isNotEmpty()) "$packageName.$classPath" else classPath
    }

    /**
     * Resolve the parent class name, handling nested classes.
     */
    private fun resolveParentClassName(
        parentClassName: String,
        packageName: String,
        currentClassName: String,
        cu: CompilationUnit,
    ): String {
        // Check if it's a reference to a sibling nested class (e.g., Layer in LayerContainer)
        // In JavaParser, this appears as just "Layer" but the JVM name is "LayerContainer$Layer"
        val outerClass = currentClassName.substringBeforeLast('$', currentClassName)

        // Handle nested class references like "Evaluator.OptionHolder" -> "package.Evaluator$OptionHolder"
        if (parentClassName.contains('.')) {
            // Convert dots to $ for nested classes and try to resolve the outer class
            val parts = parentClassName.split('.')
            val outerClassName = parts.first()
            val nestedPath = parts.drop(1).joinToString("$")

            // Try to resolve the outer class
            val resolvedOuter = resolveSimpleClassName(outerClassName, packageName, cu)
            if (resolvedOuter != null) {
                val fullName = "$resolvedOuter\$$nestedPath"
                if (signatureIndex?.getConstructors(fullName) != null) {
                    return fullName
                }
            }

            // Try as-is with dots converted to $
            val withDollar = parentClassName.replace('.', '$')
            if (packageName.isNotEmpty()) {
                val withPackage = "$packageName.$withDollar"
                if (signatureIndex?.getConstructors(withPackage) != null) {
                    return withPackage
                }
            }
        }

        // If the parent is a simple class name (no dots)
        if (!parentClassName.contains('.')) {
            // Try as nested class of outer class
            val asNestedClass = "$outerClass\$$parentClassName"
            if (signatureIndex?.getConstructors(asNestedClass) != null) {
                return asNestedClass
            }

            // Try with package prefix
            if (packageName.isNotEmpty()) {
                val withPackage = "$packageName.$parentClassName"
                if (signatureIndex?.getConstructors(withPackage) != null) {
                    return withPackage
                }
            }
        }

        // Check imports
        for (import in cu.imports) {
            if (import.isAsterisk) continue
            val importedName = import.nameAsString
            val simpleParent = parentClassName.substringBefore('.')
            if (importedName.endsWith(".$simpleParent")) {
                // Found import for outer class, append nested part
                val nestedPart = parentClassName.substringAfter('.', "")
                return if (nestedPart.isNotEmpty()) {
                    "$importedName\$${nestedPart.replace('.', '$')}"
                } else {
                    importedName
                }
            }
        }

        // Default: same package with $ for nested classes
        val normalizedName = parentClassName.replace('.', '$')
        return if (packageName.isNotEmpty()) "$packageName.$normalizedName" else normalizedName
    }

    /**
     * Resolve a simple class name (no dots) to its fully qualified form.
     */
    private fun resolveSimpleClassName(
        simpleName: String,
        packageName: String,
        cu: CompilationUnit,
    ): String? {
        // Check imports
        for (import in cu.imports) {
            if (import.isAsterisk) continue
            if (import.nameAsString.endsWith(".$simpleName")) {
                return import.nameAsString
            }
        }

        // Try same package
        if (packageName.isNotEmpty()) {
            val withPackage = "$packageName.$simpleName"
            if (signatureIndex?.getConstructors(withPackage) != null) {
                return withPackage
            }
        }

        return null
    }

    /**
     * Build a JVM method descriptor from argument expressions.
     * This is used to match against constructors in the signature index.
     */
    private fun buildDescriptorFromArguments(
        arguments: NodeList<Expression>,
        cu: CompilationUnit?,
    ): String {
        val params =
            arguments.joinToString("") { arg ->
                typeToDescriptor(getArgumentType(arg), cu)
            }
        return "($params)V"
    }

    /**
     * Get the type of an argument expression.
     * For cast expressions, returns the cast target type.
     * For literals, returns the literal type.
     */
    private fun getArgumentType(arg: Expression): Type? =
        when (arg) {
            is CastExpr -> {
                arg.type
            }

            is NullLiteralExpr -> {
                null
            }

            // Can't determine type from bare null
            is BooleanLiteralExpr -> {
                PrimitiveType.booleanType()
            }

            is IntegerLiteralExpr -> {
                PrimitiveType.intType()
            }

            is LongLiteralExpr -> {
                PrimitiveType.longType()
            }

            is DoubleLiteralExpr -> {
                val value = arg.value
                if (value.endsWith("f") || value.endsWith("F")) {
                    PrimitiveType.floatType()
                } else {
                    PrimitiveType.doubleType()
                }
            }

            is CharLiteralExpr -> {
                PrimitiveType.charType()
            }

            else -> {
                null
            }
        }

    /**
     * Convert a JavaParser Type to a JVM descriptor string.
     * Note: This uses the erased type name as it appears in the cast expression,
     * which should match the bytecode descriptor.
     */
    private fun typeToDescriptor(
        type: Type?,
        cu: CompilationUnit?,
    ): String {
        if (type == null) return "Ljava/lang/Object;"

        return when (type) {
            is PrimitiveType -> {
                when (type.type) {
                    PrimitiveType.Primitive.BOOLEAN -> "Z"
                    PrimitiveType.Primitive.BYTE -> "B"
                    PrimitiveType.Primitive.CHAR -> "C"
                    PrimitiveType.Primitive.SHORT -> "S"
                    PrimitiveType.Primitive.INT -> "I"
                    PrimitiveType.Primitive.LONG -> "J"
                    PrimitiveType.Primitive.FLOAT -> "F"
                    PrimitiveType.Primitive.DOUBLE -> "D"
                }
            }

            is ArrayType -> {
                "[" + typeToDescriptor(type.componentType, cu)
            }

            is ClassOrInterfaceType -> {
                // Get the raw type name (without generics)
                val simpleName = type.nameWithScope
                // Try to resolve the fully qualified name
                val fqn = resolveFullyQualifiedName(simpleName, cu)
                // Convert to JVM internal name format
                // Package separators become /, nested class separators become $
                val internalName = toJvmInternalName(fqn, cu)
                "L$internalName;"
            }

            else -> {
                "Ljava/lang/Object;"
            }
        }
    }

    /**
     * Convert a fully qualified name to JVM internal name format.
     * Package separators (.) become /, nested class separators become $.
     */
    private fun toJvmInternalName(
        fqn: String,
        cu: CompilationUnit?,
    ): String {
        val packageName = cu?.packageDeclaration?.map { it.nameAsString }?.orElse("") ?: ""

        // If the name starts with the package, the rest might contain nested classes
        if (packageName.isNotEmpty() && fqn.startsWith("$packageName.")) {
            val classPath = fqn.substring(packageName.length + 1) // Skip package and dot
            // The class path might be "OuterClass.InnerClass" which should become "OuterClass$InnerClass"
            val packagePart = packageName.replace('.', '/')
            val classPart = classPath.replace('.', '$')
            return "$packagePart/$classPart"
        }

        // For classes outside current package, try to find them in the signature index
        // Try progressively converting dots to $ starting from the right
        val parts = fqn.split('.')
        for (splitPoint in (parts.size - 1) downTo 1) {
            val packagePart = parts.subList(0, splitPoint).joinToString(".")
            val classPart = parts.subList(splitPoint, parts.size).joinToString("$")
            val candidateFqn = "$packagePart.$classPart"

            if (signatureIndex?.getConstructors(candidateFqn) != null) {
                return packagePart.replace('.', '/') + "/" + classPart
            }
        }

        // Default: just replace dots with slashes (no nested classes detected)
        return fqn.replace('.', '/')
    }

    /**
     * Resolve a simple or partially qualified class name to a fully qualified name.
     */
    private fun resolveFullyQualifiedName(
        name: String,
        cu: CompilationUnit?,
    ): String {
        if (cu == null) return name

        // Already fully qualified?
        if (name.contains('.') && !name.startsWith("java.") && signatureIndex?.getConstructors(name) != null) {
            return name
        }

        // Check imports
        for (import in cu.imports) {
            if (import.isAsterisk) continue
            val importedName = import.nameAsString
            if (importedName.endsWith(".$name") || importedName.endsWith(name)) {
                return importedName
            }
        }

        // Check java.lang
        val javaLangName = "java.lang.$name"
        if (name == "String" || name == "Object" || name == "Integer" || name == "Long" ||
            name == "Boolean" || name == "Character" || name == "Byte" || name == "Short" ||
            name == "Float" || name == "Double" || name == "Void" || name == "Class" ||
            name == "Throwable" || name == "Exception" || name == "RuntimeException" ||
            name == "Error" || name == "Thread" || name == "Runnable"
        ) {
            return javaLangName
        }

        // Check same package
        val packageName = cu.packageDeclaration.map { it.nameAsString }.orElse("")
        if (packageName.isNotEmpty()) {
            val samePackageName = "$packageName.$name"
            if (signatureIndex?.getConstructors(samePackageName) != null) {
                return samePackageName
            }
        }

        // Return as-is (might be in same package or unresolved)
        return if (packageName.isNotEmpty()) "$packageName.$name" else name
    }

    /**
     * Update the cast type in a cast expression if it needs generic arguments.
     *
     * @param typeParamNames Type parameter names from the target class (e.g., ["T"])
     * @param typeArgs Actual type arguments from the extends clause (e.g., ["DynamicLayerEntry"])
     */
    private fun updateCastType(
        arg: Expression,
        genericSignature: TypeSignature,
        typeParamNames: List<String>,
        typeArgs: List<String>,
        packageName: String,
        cu: CompilationUnit,
    ) {
        if (arg !is CastExpr) return

        // Build substitution map: T -> DynamicLayerEntry
        val substitutions = typeParamNames.zip(typeArgs).toMap()

        val newType = TypeSignatureRenderer.toJavaParserType(genericSignature, substitutions, packageName, cu)
        arg.setType(newType)
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

        // Replace various $assertionsDisabled patterns that can appear in decompiled code
        // Vineflower sometimes outputs these without proper class references
        result = result.replace("<unrepresentable>.\$assertionsDisabled", "true")
        result = result.replace("!.\$assertionsDisabled", "false")
        result = result.replace(".\$assertionsDisabled", "true")
        result = result.replace("!.assertionsDisabled", "false")
        result = result.replace(".assertionsDisabled", "true")
        // Handle bare $assertionsDisabled (without class prefix) - common in records
        result = result.replace("!\$assertionsDisabled", "false")
        result = result.replace("\$assertionsDisabled", "true")

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
