package io.github.hytalekt.stubs.vineflower

import org.jetbrains.java.decompiler.api.plugin.pass.Pass
import org.jetbrains.java.decompiler.api.plugin.pass.PassContext
import org.jetbrains.java.decompiler.code.CodeConstants
import org.jetbrains.java.decompiler.modules.decompiler.exps.AssignmentExprent
import org.jetbrains.java.decompiler.modules.decompiler.exps.ConstExprent
import org.jetbrains.java.decompiler.modules.decompiler.exps.Exprent
import org.jetbrains.java.decompiler.modules.decompiler.exps.FieldExprent
import org.jetbrains.java.decompiler.modules.decompiler.stats.BasicBlockStatement
import org.jetbrains.java.decompiler.modules.decompiler.stats.Statement
import org.jetbrains.java.decompiler.struct.StructClass
import org.jetbrains.java.decompiler.struct.gen.CodeType
import org.jetbrains.java.decompiler.struct.gen.FieldDescriptor
import org.jetbrains.java.decompiler.struct.gen.VarType

/**
 * Vineflower pass that handles enum-specific transformations.
 *
 * This pass:
 * - Replaces enum constructor bodies with empty bodies
 * - Handles enum static initializers (skips enum constants, sets other static finals to defaults)
 * - Skips abstract methods in enums (per-constant implementations are stripped)
 */
class EnumPass : Pass {
    override fun run(ctx: PassContext): Boolean {
        val method = ctx.method
        val root = ctx.root
        val enclosingClass = ctx.enclosingClass

        // Only handle enum classes
        if (!enclosingClass.hasModifier(CodeConstants.ACC_ENUM)) {
            return false
        }

        // Skip abstract methods (enums with per-constant implementations may have abstract methods)
        if (method.hasModifier(CodeConstants.ACC_ABSTRACT)) {
            return false
        }

        // Skip native methods
        if (method.hasModifier(CodeConstants.ACC_NATIVE)) {
            return false
        }

        // Handle enum constructors - replace with empty body
        // Java will auto-generate the default constructor when we remove it in post-processing
        if (method.name == CodeConstants.INIT_NAME) {
            root.replaceStatement(root.first, createEmptyBody())
            return true
        }

        // Handle enum static initializers - set static final fields to defaults, skip enum constants
        if (method.name == CodeConstants.CLINIT_NAME) {
            val staticInitBody = createEnumStaticInitializerBody(enclosingClass)
            root.replaceStatement(root.first, staticInitBody)
            return true
        }

        // Regular enum methods are handled by RemoveMethodBodiesPass
        return false
    }

    private fun createEmptyBody(): Statement {
        return BasicBlockStatement.create().apply {
            exprents = mutableListOf()
        }
    }

    private fun createEnumStaticInitializerBody(enclosingClass: StructClass): Statement {
        val assignments = mutableListOf<Exprent>()

        // Generate default value assignments for static final fields
        // Skip enum constants (they have ACC_ENUM modifier on the field)
        for (field in enclosingClass.fields) {
            if (field.hasModifier(CodeConstants.ACC_STATIC) &&
                field.hasModifier(CodeConstants.ACC_FINAL) &&
                !field.hasModifier(CodeConstants.ACC_ENUM)
            ) {
                val fieldDescriptor = FieldDescriptor.parseDescriptor(field.descriptor)
                val fieldType = fieldDescriptor.type

                // Create field reference (static field access)
                val fieldExprent = FieldExprent(
                    field.name,
                    enclosingClass.qualifiedName,
                    true, // isStatic
                    null, // instance (null for static)
                    fieldDescriptor,
                    null, // bytecodeOffsets
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
}
