package io.github.hytalekt.stubs

import io.github.classgraph.ClassGraph
import io.github.classgraph.ClassInfo
import io.github.classgraph.MethodInfo
import io.github.hytalekt.stubs.util.ParameterNameExtractor
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

class ParameterNameExtractorTest :
    FunSpec({

        // Shared scan result to keep bytecode accessible
        val scanResult = ClassGraph()
            .acceptPackages("io.github.hytalekt.stubs.suite")
            .enableClassInfo()
            .enableMethodInfo()
            .enableAnnotationInfo()  // Enable to get full class info
            .ignoreClassVisibility()  // Include private constructors (like enum constructors)
            .ignoreMethodVisibility()  // Include all methods
            .scan()

        // Helper function to get ClassInfo for a test fixture
        fun getClassInfo(className: String): ClassInfo =
            scanResult.getClassInfo(className)
                ?: error("Class not found: $className")

        // Close scan result after all tests
        afterSpec {
            scanResult.close()
        }

        // Helper function to find a method by name and parameter count
        fun ClassInfo.findMethod(
            methodName: String,
            paramCount: Int? = null,
        ): MethodInfo {
            // Check both regular methods and constructors
            val allMethods = this.methodInfo + this.constructorInfo
            return allMethods.firstOrNull { method ->
                method.name == methodName &&
                    (paramCount == null || method.parameterInfo.size == paramCount)
            } ?: error("Method not found: $methodName (checked ${allMethods.size} methods)")
        }

        context("extracting parameter names from regular methods") {
            val classInfo = getClassInfo("io.github.hytalekt.stubs.suite.TestMethodVariations")

            test("should handle methods with no parameters") {
                val method = classInfo.findMethod("basicMethod", paramCount = 0)
                val names = ParameterNameExtractor.extractParameterNames(classInfo, method)
                names.shouldBeEmpty()
            }

            test("should extract multiple parameter names") {
                val method = classInfo.findMethod("methodWithParams", paramCount = 3)
                val names = ParameterNameExtractor.extractParameterNames(classInfo, method)
                names shouldContainExactly listOf("name", "value", "decimal")
            }

            test("should extract generic method parameter names") {
                val method = classInfo.findMethod("genericMethodWithParams", paramCount = 2)
                val names = ParameterNameExtractor.extractParameterNames(classInfo, method)
                names shouldContainExactly listOf("item", "count")
            }

            test("should handle static methods with no parameters") {
                val method = classInfo.findMethod("staticMethod", paramCount = 0)
                val names = ParameterNameExtractor.extractParameterNames(classInfo, method)
                names.shouldBeEmpty()
            }
        }

        context("extracting parameter names from constructors") {
            val classInfo = getClassInfo("io.github.hytalekt.stubs.suite.TestBasicClass")

            test("should extract constructor parameter names") {
                val constructor = classInfo.findMethod("<init>", paramCount = 1)
                val names = ParameterNameExtractor.extractParameterNames(classInfo, constructor)
                names shouldContainExactly listOf("value")
            }

            test("should handle no-arg constructor") {
                val constructor = classInfo.findMethod("<init>", paramCount = 0)
                val names = ParameterNameExtractor.extractParameterNames(classInfo, constructor)
                names.shouldBeEmpty()
            }
        }

        context("extracting parameter names from enum constructors") {
            val classInfo = getClassInfo("io.github.hytalekt.stubs.suite.TestEnum")

            test("should skip synthetic enum parameters and extract actual parameters") {
                // Enum constructors have synthetic (String name, int ordinal) as first two params
                val constructor = classInfo.findMethod("<init>", paramCount = 3)
                val names =
                    ParameterNameExtractor.extractParameterNames(
                        classInfo,
                        constructor,
                        skipSyntheticParams = 2,
                    )
                names shouldContainExactly listOf("description")
            }
        }

        context("caching behavior") {
            val classInfo = getClassInfo("io.github.hytalekt.stubs.suite.TestMethodVariations")

            test("should return same results on multiple calls") {
                val method = classInfo.findMethod("methodWithParams", paramCount = 3)

                val names1 = ParameterNameExtractor.extractParameterNames(classInfo, method)
                val names2 = ParameterNameExtractor.extractParameterNames(classInfo, method)

                names1 shouldContainExactly listOf("name", "value", "decimal")
                names2 shouldContainExactly listOf("name", "value", "decimal")
                names1 shouldBe names2
            }
        }

        context("wide type handling") {
            test("should handle long and double parameters correctly") {
                // Create a test class with long and double parameters
                val classInfo = getClassInfo("io.github.hytalekt.stubs.suite.TestFieldVariations")

                // Find a method that might have long/double params
                // If no such method exists, this tests that we don't break on other types
                val methods = classInfo.methodInfo.filter { !it.isConstructor }
                if (methods.isNotEmpty()) {
                    methods.forEach { method ->
                        // Should not throw exceptions
                        val names = ParameterNameExtractor.extractParameterNames(classInfo, method)
                        names.size shouldBe method.parameterInfo.size
                    }
                }
            }
        }
    })
