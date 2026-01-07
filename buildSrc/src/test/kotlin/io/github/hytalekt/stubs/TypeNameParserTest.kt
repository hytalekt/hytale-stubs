package io.github.hytalekt.stubs

import com.palantir.javapoet.ArrayTypeName
import com.palantir.javapoet.ClassName
import com.palantir.javapoet.ParameterizedTypeName
import com.palantir.javapoet.TypeName
import com.palantir.javapoet.WildcardTypeName
import io.github.hytalekt.stubs.util.OBJECT_CLASS
import io.github.hytalekt.stubs.util.boxPrimitiveIfNeeded
import io.github.hytalekt.stubs.util.parseTypeName
import io.github.hytalekt.stubs.util.splitTypeArguments
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class TypeNameParserTest :
    FunSpec({

        context("Primitive types") {
            withData(
                "void" to TypeName.VOID,
                "V" to TypeName.VOID,
                "boolean" to TypeName.BOOLEAN,
                "Z" to TypeName.BOOLEAN,
                "byte" to TypeName.BYTE,
                "B" to TypeName.BYTE,
                "char" to TypeName.CHAR,
                "C" to TypeName.CHAR,
                "short" to TypeName.SHORT,
                "S" to TypeName.SHORT,
                "int" to TypeName.INT,
                "I" to TypeName.INT,
                "long" to TypeName.LONG,
                "J" to TypeName.LONG,
                "float" to TypeName.FLOAT,
                "F" to TypeName.FLOAT,
                "double" to TypeName.DOUBLE,
                "D" to TypeName.DOUBLE,
            ) { (input, expected) ->
                parseTypeName(input) shouldBe expected
            }

            test("handles whitespace around primitives") {
                parseTypeName("  int  ") shouldBe TypeName.INT
                parseTypeName("\tboolean\n") shouldBe TypeName.BOOLEAN
            }
        }

        context("Array types") {
            test("parses primitive array with bracket suffix") {
                val result = parseTypeName("int[]")
                result.shouldBeInstanceOf<ArrayTypeName>()
                result.componentType() shouldBe TypeName.INT
            }

            test("parses reference array with bracket suffix") {
                val result = parseTypeName("java.lang.String[]")
                result.shouldBeInstanceOf<ArrayTypeName>()
                result.componentType() shouldBe ClassName.get("java.lang", "String")
            }

            test("parses array with JVM prefix notation") {
                val result = parseTypeName("[I")
                result.shouldBeInstanceOf<ArrayTypeName>()
                result.componentType() shouldBe TypeName.INT
            }

            test("parses multi-dimensional array") {
                val result = parseTypeName("int[][]")
                result.shouldBeInstanceOf<ArrayTypeName>()
                val inner = result.componentType()
                inner.shouldBeInstanceOf<ArrayTypeName>()
                inner.componentType() shouldBe TypeName.INT
            }

            test("parses array of parameterized type") {
                val result = parseTypeName("java.util.List<java.lang.String>[]")
                result.shouldBeInstanceOf<ArrayTypeName>()
                result.componentType().shouldBeInstanceOf<ParameterizedTypeName>()
            }
        }

        context("Wildcard types") {
            test("parses unbounded wildcard") {
                val result = parseTypeName("?")
                result.shouldBeInstanceOf<WildcardTypeName>()
                result.upperBounds() shouldBe listOf(OBJECT_CLASS)
            }

            test("parses upper bounded wildcard") {
                val result = parseTypeName("? extends java.lang.Number")
                result.shouldBeInstanceOf<WildcardTypeName>()
                result.upperBounds() shouldBe listOf(ClassName.get("java.lang", "Number"))
            }

            test("parses lower bounded wildcard") {
                val result = parseTypeName("? super java.lang.Integer")
                result.shouldBeInstanceOf<WildcardTypeName>()
                result.lowerBounds() shouldBe listOf(ClassName.get("java.lang", "Integer"))
            }

            test("parses wildcard with parameterized bound") {
                val result = parseTypeName("? extends java.util.List<java.lang.String>")
                result.shouldBeInstanceOf<WildcardTypeName>()
                val upperBound = result.upperBounds().first()
                upperBound.shouldBeInstanceOf<ParameterizedTypeName>()
            }
        }

        context("Parameterized types") {
            test("parses simple parameterized type") {
                val result = parseTypeName("java.util.List<java.lang.String>")
                result.shouldBeInstanceOf<ParameterizedTypeName>()
                result.rawType() shouldBe ClassName.get("java.util", "List")
                result.typeArguments() shouldHaveSize 1
                result.typeArguments()[0] shouldBe ClassName.get("java.lang", "String")
            }

            test("parses parameterized type with multiple arguments") {
                val result = parseTypeName("java.util.Map<java.lang.String, java.lang.Integer>")
                result.shouldBeInstanceOf<ParameterizedTypeName>()
                result.rawType() shouldBe ClassName.get("java.util", "Map")
                result.typeArguments() shouldHaveSize 2
                result.typeArguments()[0] shouldBe ClassName.get("java.lang", "String")
                result.typeArguments()[1] shouldBe ClassName.get("java.lang", "Integer")
            }

            test("parses nested parameterized types") {
                val result = parseTypeName("java.util.List<java.util.List<java.lang.String>>")
                result.shouldBeInstanceOf<ParameterizedTypeName>()
                val innerType = result.typeArguments()[0]
                innerType.shouldBeInstanceOf<ParameterizedTypeName>()
                innerType.rawType() shouldBe ClassName.get("java.util", "List")
            }

            test("parses deeply nested parameterized types") {
                val result =
                    parseTypeName(
                        "java.util.Map<java.lang.String, java.util.List<java.util.Set<java.lang.Integer>>>",
                    )
                result.shouldBeInstanceOf<ParameterizedTypeName>()
                result.typeArguments() shouldHaveSize 2
            }

            test("boxes primitive type arguments") {
                val result = parseTypeName("java.util.List<int>")
                result.shouldBeInstanceOf<ParameterizedTypeName>()
                result.typeArguments()[0] shouldBe ClassName.get("java.lang", "Integer")
            }

            test("parses parameterized type with wildcard argument") {
                val result = parseTypeName("java.util.List<?>")
                result.shouldBeInstanceOf<ParameterizedTypeName>()
                result.typeArguments()[0].shouldBeInstanceOf<WildcardTypeName>()
            }

            test("handles empty generic brackets gracefully") {
                val result = parseTypeName("java.util.List<>")
                result.shouldBeInstanceOf<ClassName>()
            }
        }

        context("Class names") {
            test("parses simple class name") {
                val result = parseTypeName("String")
                result.shouldBeInstanceOf<ClassName>()
                result.simpleName() shouldBe "String"
            }

            test("parses fully qualified class name") {
                val result = parseTypeName("java.lang.String")
                result shouldBe ClassName.get("java.lang", "String")
            }

            test("parses JVM internal format with L prefix and semicolon suffix") {
                val result = parseTypeName("Ljava/lang/String;")
                result shouldBe ClassName.get("java.lang", "String")
            }

            test("parses JVM internal format with slashes") {
                val result = parseTypeName("java/util/List")
                result shouldBe ClassName.get("java.util", "List")
            }

            test("parses nested class") {
                val result = parseTypeName("java.util.Map.Entry")
                result.shouldBeInstanceOf<ClassName>()
                result.simpleName() shouldBe "Entry"
            }
        }

        context("Split type arguments") {
            test("splits simple arguments") {
                val result = splitTypeArguments("java.lang.String, java.lang.Integer")
                result shouldBe listOf("java.lang.String", "java.lang.Integer")
            }

            test("preserves nested generics") {
                val result =
                    splitTypeArguments(
                        "java.lang.String, java.util.List<java.lang.Integer>",
                    )
                result shouldBe
                    listOf(
                        "java.lang.String",
                        "java.util.List<java.lang.Integer>",
                    )
            }

            test("handles deeply nested generics") {
                val result =
                    splitTypeArguments(
                        "java.util.Map<java.lang.String, java.lang.Integer>, java.util.List<java.util.Set<java.lang.Long>>",
                    )
                result shouldHaveSize 2
                result[0] shouldBe "java.util.Map<java.lang.String, java.lang.Integer>"
                result[1] shouldBe "java.util.List<java.util.Set<java.lang.Long>>"
            }

            test("handles single argument") {
                val result = splitTypeArguments("java.lang.String")
                result shouldBe listOf("java.lang.String")
            }

            test("handles empty input") {
                val result = splitTypeArguments("")
                result.shouldBeEmpty()
            }

            test("trims whitespace from arguments") {
                val result = splitTypeArguments("  java.lang.String  ,  java.lang.Integer  ")
                result shouldBe listOf("java.lang.String", "java.lang.Integer")
            }
        }

        context("Box primitive if needed") {
            withData(
                TypeName.VOID to ClassName.get("java.lang", "Void"),
                TypeName.BOOLEAN to ClassName.get("java.lang", "Boolean"),
                TypeName.BYTE to ClassName.get("java.lang", "Byte"),
                TypeName.CHAR to ClassName.get("java.lang", "Character"),
                TypeName.SHORT to ClassName.get("java.lang", "Short"),
                TypeName.INT to ClassName.get("java.lang", "Integer"),
                TypeName.LONG to ClassName.get("java.lang", "Long"),
                TypeName.FLOAT to ClassName.get("java.lang", "Float"),
                TypeName.DOUBLE to ClassName.get("java.lang", "Double"),
            ) { (primitive, expected) ->
                boxPrimitiveIfNeeded(primitive) shouldBe expected
            }

            test("returns reference types unchanged") {
                val className = ClassName.get("java.lang", "String")
                boxPrimitiveIfNeeded(className) shouldBe className
            }

            test("returns array types unchanged") {
                val arrayType = ArrayTypeName.of(TypeName.INT)
                boxPrimitiveIfNeeded(arrayType) shouldBe arrayType
            }
        }

        context("Edge cases") {
            test("handles complex real-world type") {
                val result =
                    parseTypeName(
                        "java.util.Map<java.lang.String, java.util.List<? extends java.lang.Number>>",
                    )
                result.shouldBeInstanceOf<ParameterizedTypeName>()
                result.rawType() shouldBe ClassName.get("java.util", "Map")

                val valueType = result.typeArguments()[1]
                valueType.shouldBeInstanceOf<ParameterizedTypeName>()

                val wildcardArg = valueType.typeArguments()[0]
                wildcardArg.shouldBeInstanceOf<WildcardTypeName>()
            }

            test("handles array of wildcards in parameterized type") {
                val result = parseTypeName("java.util.List<java.lang.String[]>")
                result.shouldBeInstanceOf<ParameterizedTypeName>()
                result.typeArguments()[0].shouldBeInstanceOf<ArrayTypeName>()
            }
        }
    })
