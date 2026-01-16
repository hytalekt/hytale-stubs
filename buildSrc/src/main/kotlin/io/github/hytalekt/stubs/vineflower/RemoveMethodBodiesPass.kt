package io.github.hytalekt.stubs.vineflower

import org.jetbrains.java.decompiler.api.plugin.pass.Pass
import org.jetbrains.java.decompiler.api.plugin.pass.PassContext
import org.jetbrains.java.decompiler.code.CodeConstants
import org.jetbrains.java.decompiler.main.DecompilerContext
import org.jetbrains.java.decompiler.modules.decompiler.exps.AssignmentExprent
import org.jetbrains.java.decompiler.modules.decompiler.exps.ConstExprent
import org.jetbrains.java.decompiler.modules.decompiler.exps.ExitExprent
import org.jetbrains.java.decompiler.modules.decompiler.exps.Exprent
import org.jetbrains.java.decompiler.modules.decompiler.exps.FieldExprent
import org.jetbrains.java.decompiler.modules.decompiler.exps.InvocationExprent
import org.jetbrains.java.decompiler.modules.decompiler.exps.NewExprent
import org.jetbrains.java.decompiler.modules.decompiler.exps.VarExprent
import org.jetbrains.java.decompiler.modules.decompiler.stats.BasicBlockStatement
import org.jetbrains.java.decompiler.modules.decompiler.stats.SequenceStatement
import org.jetbrains.java.decompiler.modules.decompiler.stats.Statement
import org.jetbrains.java.decompiler.struct.StructClass
import org.jetbrains.java.decompiler.struct.StructField
import org.jetbrains.java.decompiler.struct.StructMethod
import org.jetbrains.java.decompiler.struct.gen.CodeType
import org.jetbrains.java.decompiler.struct.gen.MethodDescriptor
import org.jetbrains.java.decompiler.struct.gen.VarType
import org.jetbrains.java.decompiler.struct.gen.FieldDescriptor

class RemoveMethodBodiesPass : Pass {
    override fun run(ctx: PassContext): Boolean {
        val method = ctx.method
        val root = ctx.root
        val enclosingClass = ctx.enclosingClass

        // Skip abstract and native methods (they have no body)
        if (method.hasModifier(CodeConstants.ACC_ABSTRACT) || method.hasModifier(CodeConstants.ACC_NATIVE)) {
            return false
        }

        // Skip enum constructors and static initializers - handled by EnumPass
        // Regular enum methods still need to be handled here
        if (enclosingClass.hasModifier(CodeConstants.ACC_ENUM) &&
            (method.name == CodeConstants.INIT_NAME || method.name == CodeConstants.CLINIT_NAME)
        ) {
            return false
        }

        // Handle static initializers - replace with default value assignments for static final fields
        if (method.name == CodeConstants.CLINIT_NAME) {
            val staticInitBody = createStaticInitializerBody(enclosingClass)
            root.replaceStatement(root.first, staticInitBody)
            return true
        }

        // Handle regular constructors - preserve super()/this() call, replace rest with throw
        if (method.name == CodeConstants.INIT_NAME) {
            val firstBlock = findFirstBasicBlock(root.first)
            val superOrThisCall = firstBlock?.exprents?.firstOrNull()?.let { first ->
                if (first is InvocationExprent && first.functype == InvocationExprent.Type.INIT) first else null
            }

            // Build new body with just super()/this() (if present) + throw
            val newBody = BasicBlockStatement.create().apply {
                exprents = if (superOrThisCall != null) {
                    mutableListOf(superOrThisCall, createThrowExprent(method))
                } else {
                    mutableListOf(createThrowExprent(method))
                }
            }

            // Replace entire body
            root.replaceStatement(root.first, newBody)
            return true
        }

        // Handle regular methods - replace with throw
        val stubStatement = createThrowStatement(method)
        root.replaceStatement(root.first, stubStatement)

        return true
    }

    private fun findFirstBasicBlock(statement: Statement?): BasicBlockStatement? {
        if (statement == null) return null
        if (statement is BasicBlockStatement && statement.exprents != null) {
            return statement
        }
        // For SequenceStatement, check first child
        if (statement is SequenceStatement && statement.stats.isNotEmpty()) {
            return findFirstBasicBlock(statement.stats[0])
        }
        // Check all children for other statement types
        for (child in statement.stats) {
            val result = findFirstBasicBlock(child)
            if (result != null) return result
        }
        return null
    }

    private fun createStaticInitializerBody(enclosingClass: StructClass): Statement {
        val assignments = mutableListOf<Exprent>()

        // Generate default value assignments for all static final fields
        // Skip enum constants (they have ACC_ENUM modifier on the field)
        for (field in enclosingClass.fields) {
            if (field.hasModifier(CodeConstants.ACC_STATIC) &&
                field.hasModifier(CodeConstants.ACC_FINAL) &&
                !field.hasModifier(CodeConstants.ACC_ENUM)) {

                val fieldDescriptor = FieldDescriptor.parseDescriptor(field.descriptor)
                val fieldType = fieldDescriptor.type

                // Create field reference (static field access)
                val fieldExprent = FieldExprent(
                    field.name,
                    enclosingClass.qualifiedName,
                    true, // isStatic
                    null, // instance (null for static)
                    fieldDescriptor,
                    null // bytecodeOffsets
                )

                // Create default value for the field type
                val defaultValue = createDefaultExprent(fieldType)

                // Create assignment: FieldName = defaultValue
                val assignment = AssignmentExprent(fieldExprent, defaultValue, null)
                assignments.add(assignment)
            }
        }

        return BasicBlockStatement.create().apply {
            exprents = assignments
        }
    }

    private fun createConstructorBody(method: StructMethod, enclosingClass: StructClass): Statement {
        val exprents = mutableListOf<Exprent>()

        // Add super() call if class has a superclass other than Object
        val superClassName = enclosingClass.superClass?.string
        if (superClassName != null && superClassName != "java/lang/Object") {
            val superCall = createSuperCall(superClassName, enclosingClass)
            if (superCall != null) {
                exprents.add(superCall)
            }
        }

        // Add throw GeneratedStubException
        exprents.add(createThrowExprent(method))

        return BasicBlockStatement.create().apply {
            this.exprents = exprents
        }
    }

    private fun createSuperCall(superClassName: String, enclosingClass: StructClass): InvocationExprent? {
        val superInvocation = InvocationExprent()
        superInvocation.name = CodeConstants.INIT_NAME
        superInvocation.classname = superClassName
        superInvocation.functype = InvocationExprent.Type.INIT
        superInvocation.isStatic = false

        // Try to find superclass to determine constructor signature
        val superStruct = DecompilerContext.getStructContext()?.getClass(superClassName)

        if (superStruct != null) {
            // Find a suitable constructor - prefer no-arg, otherwise use one with fewest params
            val constructor = findSuitableConstructor(superStruct)
            if (constructor != null) {
                val descriptor = MethodDescriptor.parseDescriptor(constructor.descriptor)
                superInvocation.descriptor = descriptor
                superInvocation.stringDescriptor = constructor.descriptor

                // Generate default arguments for each parameter
                val params = descriptor.params.map { createDefaultExprent(it) }
                superInvocation.lstParameters = params.toMutableList()
            } else {
                // No constructor found - default to no-arg
                setNoArgConstructor(superInvocation)
            }
        } else {
            // Superclass not available - assume no-arg constructor
            setNoArgConstructor(superInvocation)
        }

        // Set instance to 'this'
        val thisType = VarType(CodeType.OBJECT, 0, enclosingClass.qualifiedName)
        superInvocation.instance = VarExprent(0, thisType, null)

        return superInvocation
    }

    private fun findSuitableConstructor(structClass: StructClass): StructMethod? {
        val constructors = structClass.methods.filter { it.name == CodeConstants.INIT_NAME }

        if (constructors.isEmpty()) return null

        // Prefer no-arg constructor
        val noArg = constructors.find { it.descriptor == "()V" }
        if (noArg != null) return noArg

        // Otherwise, pick constructor with fewest parameters
        return constructors.minByOrNull {
            MethodDescriptor.parseDescriptor(it.descriptor).params.size
        }
    }

    private fun setNoArgConstructor(invocation: InvocationExprent) {
        invocation.descriptor = MethodDescriptor.parseDescriptor("()V")
        invocation.stringDescriptor = "()V"
        invocation.lstParameters = mutableListOf()
    }

    private fun createDefaultExprent(varType: VarType): Exprent {
        // Array types should always be null
        if (varType.arrayDim > 0) {
            return ConstExprent(VarType.VARTYPE_NULL, null, null)
        }

        return when (varType.type) {
            CodeType.BOOLEAN -> ConstExprent(VarType.VARTYPE_BOOLEAN, 0, null)
            CodeType.BYTE -> ConstExprent(VarType.VARTYPE_BYTE, 0, null)
            CodeType.CHAR -> ConstExprent(VarType.VARTYPE_CHAR, 0, null)
            CodeType.SHORT -> ConstExprent(VarType.VARTYPE_SHORT, 0, null)
            CodeType.INT -> ConstExprent(VarType.VARTYPE_INT, 0, null)
            CodeType.LONG -> ConstExprent(VarType.VARTYPE_LONG, 0L, null)
            CodeType.FLOAT -> ConstExprent(VarType.VARTYPE_FLOAT, 0.0f, null)
            CodeType.DOUBLE -> ConstExprent(VarType.VARTYPE_DOUBLE, 0.0, null)
            else -> ConstExprent(VarType.VARTYPE_NULL, null, null)
        }
    }

    private fun createThrowStatement(method: StructMethod): Statement {
        return BasicBlockStatement.create().apply {
            exprents = mutableListOf(createThrowExprent(method))
        }
    }

    private fun createThrowExprent(method: StructMethod): ExitExprent {
        val ctor = InvocationExprent()
        ctor.name = CodeConstants.INIT_NAME
        ctor.classname = "io/github/hytalekt/stubs/GeneratedStubException"
        ctor.functype = InvocationExprent.Type.INIT
        ctor.descriptor = MethodDescriptor.parseDescriptor("()V")
        ctor.stringDescriptor = "()V"

        val exceptionType = VarType(CodeType.OBJECT, 0, "io/github/hytalekt/stubs/GeneratedStubException")
        val newExpr = NewExprent(exceptionType, ArrayList<Exprent>(), null)
        newExpr.constructor = ctor

        return ExitExprent(
            ExitExprent.Type.THROW,
            newExpr,
            null,
            null,
            method.methodDescriptor(),
        )
    }
}
