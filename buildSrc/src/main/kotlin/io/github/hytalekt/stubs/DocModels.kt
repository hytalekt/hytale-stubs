package io.github.hytalekt.stubs

/**
 * Documentation for a method parameter.
 */
data class ParamDoc(
    val description: String? = null,
)

/**
 * Documentation for a thrown exception.
 */
data class ThrowsDoc(
    val description: String? = null,
)

/**
 * Documentation for a method.
 */
data class MethodDoc(
    val documentation: String? = null,
    val returns: String? = null,
    val params: Map<String, ParamDoc>? = null,
    val throws: Map<String, ThrowsDoc>? = null,
    val since: String? = null,
    val deprecated: String? = null,
    val see: List<String>? = null,
)

/**
 * Documentation for a constructor.
 */
data class ConstructorDoc(
    val documentation: String? = null,
    val params: Map<String, ParamDoc>? = null,
    val throws: Map<String, ThrowsDoc>? = null,
    val since: String? = null,
    val deprecated: String? = null,
    val see: List<String>? = null,
)

/**
 * Documentation for a field.
 */
data class FieldDoc(
    val documentation: String? = null,
    val since: String? = null,
    val deprecated: String? = null,
    val see: List<String>? = null,
)

/**
 * Documentation for an enum constant.
 */
data class EnumConstantDoc(
    val documentation: String? = null,
    val since: String? = null,
    val deprecated: String? = null,
    val see: List<String>? = null,
)

/**
 * Documentation for a class, interface, enum, or record
 */
data class ClassDoc(
    val documentation: String? = null,
    val since: String? = null,
    val deprecated: String? = null,
    val author: List<String>? = null,
    val see: List<String>? = null,
    val methods: Map<String, MethodDoc>? = null,
    val constructors: Map<String, ConstructorDoc>? = null,
    val fields: Map<String, FieldDoc>? = null,
    val enumConstants: Map<String, EnumConstantDoc>? = null,
)
