package io.github.hytalekt.stubs.vineflower

import org.jetbrains.java.decompiler.api.plugin.pass.Pass
import org.jetbrains.java.decompiler.api.plugin.pass.PassContext
import org.jetbrains.java.decompiler.code.CodeConstants
import org.jetbrains.java.decompiler.modules.decompiler.exps.ConstExprent
import org.jetbrains.java.decompiler.modules.decompiler.exps.Exprent
import org.jetbrains.java.decompiler.modules.decompiler.exps.FunctionExprent
import org.jetbrains.java.decompiler.modules.decompiler.exps.InvocationExprent
import org.jetbrains.java.decompiler.modules.decompiler.stats.Statement
import org.jetbrains.java.decompiler.struct.gen.CodeType
import org.jetbrains.java.decompiler.struct.gen.VarType

/**
 * Vineflower pass that replaces super()/this() call arguments with default values.
 *
 * This handles Java 25 flexible constructors where super/this may appear anywhere
 * in the constructor body, not just as the first statement.
 */
class ConstructorStubPass : Pass {
    override fun run(ctx: PassContext): Boolean {
        val method = ctx.method

        // Only handle constructors
        if (method.name != CodeConstants.INIT_NAME) return false

        // Skip enums (handled by post-processor which removes constructors entirely)
        if (ctx.enclosingClass.hasModifier(CodeConstants.ACC_ENUM)) return false

        // Find super()/this() call anywhere in the constructor body
        val superOrThisCall = findInitCall(ctx.root.first)

        if (superOrThisCall != null) {
            // Replace arguments with default values based on the invoked method's descriptor
            val descriptor = superOrThisCall.descriptor
            val newParams = descriptor.params.map { createDefaultExprent(it) }
            superOrThisCall.lstParameters = newParams.toMutableList()
            return true
        }

        return false
    }

    /**
     * Recursively search for an InvocationExprent with functype == INIT (super/this call).
     * This handles Java 25 flexible constructors where super/this may not be the first statement.
     */
    private fun findInitCall(statement: Statement?): InvocationExprent? {
        if (statement == null) return null

        // Check exprents in this statement
        val exprents = statement.exprents
        if (exprents != null) {
            for (exprent in exprents) {
                val found = findInitCallInExprent(exprent)
                if (found != null) return found
            }
        }

        // Recursively check child statements
        for (child in statement.stats) {
            val found = findInitCall(child)
            if (found != null) return found
        }

        return null
    }

    /**
     * Search for an init call within an exprent tree.
     */
    private fun findInitCallInExprent(exprent: Exprent?): InvocationExprent? {
        if (exprent == null) return null

        if (exprent is InvocationExprent && exprent.functype == InvocationExprent.Type.INIT) {
            return exprent
        }

        // Recursively search all sub-expressions
        for (subExprent in exprent.allExprents) {
            val found = findInitCallInExprent(subExprent)
            if (found != null) return found
        }

        return null
    }

    /**
     * Create a default value exprent for the given type.
     * For reference types and arrays, creates a cast expression like `(Type) null` to avoid ambiguity
     * when multiple constructor overloads exist.
     */
    private fun createDefaultExprent(varType: VarType): Exprent {
        // Arrays always need (ArrayType) null, regardless of element type
        if (varType.arrayDim > 0) {
            return createCastToNull(varType)
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
            else -> createCastToNull(varType)
        }
    }

    /**
     * Create a cast expression: (Type) null
     * This avoids ambiguity when multiple constructor overloads have similar signatures.
     */
    private fun createCastToNull(varType: VarType): Exprent {
        val nullExprent = ConstExprent(VarType.VARTYPE_NULL, null, null)
        val typeExprent = ConstExprent(varType, null, null)
        return FunctionExprent(
            FunctionExprent.FunctionType.CAST,
            listOf(nullExprent, typeExprent),
            null,
        )
    }
}
