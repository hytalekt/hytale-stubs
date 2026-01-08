package io.github.hytalekt.stubs

import com.palantir.javapoet.JavaFile
import io.github.classgraph.ClassGraph
import io.github.hytalekt.stubs.spec.AnnotationTypeSpecBuilder
import io.github.hytalekt.stubs.spec.ClassTypeSpecBuilder
import io.github.hytalekt.stubs.spec.EnumTypeSpecBuilder
import io.github.hytalekt.stubs.spec.InterfaceTypeSpecBuilder
import io.github.hytalekt.stubs.spec.RecordTypeSpecBuilder
import io.github.hytalekt.stubs.spec.TypeSpecBuilder
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf

class TypeSpecBuilderTest :
    FunSpec({
        val classGraph =
            ClassGraph()
                .acceptPackages("io.github.hytalekt.stubs.suite")
                .enableAllInfo()

        val scanResult = classGraph.scan()

        afterSpec {
            scanResult.close()
        }

        fun generateJavaCode(
            packageName: String,
            typeSpec: com.palantir.javapoet.TypeSpec,
        ): String = JavaFile.builder(packageName, typeSpec).build().toString()

        context("ClassTypeSpecBuilder") {
            test("should build basic class with fields and methods") {
                val classInfo = scanResult.getClassInfo("io.github.hytalekt.stubs.suite.TestBasicClass")
                classInfo.shouldNotBeNull()

                val builder = ClassTypeSpecBuilder(classInfo)
                val typeSpec = builder.build()

                val code = generateJavaCode("io.github.hytalekt.stubs.suite", typeSpec)

                code shouldContain "public class TestBasicClass"
                code shouldContain "private String privateField"
                code shouldContain "public String publicField"
                code shouldContain "protected int protectedField"
                code shouldContain "static String staticField"
                code shouldContain "public String getValue()"
                code shouldContain "protected void protectedMethod()"
                code shouldContain "static void staticMethod()"
                code shouldContain "public TestBasicClass()"
                code shouldContain "public TestBasicClass(String"
            }

            test("should build generic class with type parameters") {
                val classInfo = scanResult.getClassInfo("io.github.hytalekt.stubs.suite.TestGenericClass")
                classInfo.shouldNotBeNull()

                val builder = ClassTypeSpecBuilder(classInfo)
                val typeSpec = builder.build()

                val code = generateJavaCode("io.github.hytalekt.stubs.suite", typeSpec)

                code shouldContain "public class TestGenericClass<T, U extends Number>"
                code shouldContain "private T value"
                code shouldContain "public T getValue()"
                code shouldContain "public void setValue(T"
            }

            test("should build class with superclass") {
                val classInfo = scanResult.getClassInfo("io.github.hytalekt.stubs.suite.TestExtendingClass")
                classInfo.shouldNotBeNull()

                val builder = ClassTypeSpecBuilder(classInfo)
                val typeSpec = builder.build()

                val code = generateJavaCode("io.github.hytalekt.stubs.suite", typeSpec)

                code shouldContain "class TestExtendingClass extends TestAbstractClass"
            }

            test("should build class with multiple interfaces") {
                val classInfo = scanResult.getClassInfo("io.github.hytalekt.stubs.suite.TestMultipleInheritance")
                classInfo.shouldNotBeNull()

                val builder = ClassTypeSpecBuilder(classInfo)
                val typeSpec = builder.build()

                val code = generateJavaCode("io.github.hytalekt.stubs.suite", typeSpec)

                code shouldContain "class TestMultipleInheritance implements"
            }

            test("should build abstract class with abstract methods") {
                val classInfo = scanResult.getClassInfo("io.github.hytalekt.stubs.suite.TestAbstractClass")
                classInfo.shouldNotBeNull()

                val builder = ClassTypeSpecBuilder(classInfo)
                val typeSpec = builder.build()

                val code = generateJavaCode("io.github.hytalekt.stubs.suite", typeSpec)

                code shouldContain "public abstract class TestAbstractClass"
                code shouldContain "abstract String abstractMethod()"
            }

            test("should build annotated class") {
                val classInfo = scanResult.getClassInfo("io.github.hytalekt.stubs.suite.TestAnnotatedClass")
                classInfo.shouldNotBeNull()

                val builder = ClassTypeSpecBuilder(classInfo)
                val typeSpec = builder.build()

                val code = generateJavaCode("io.github.hytalekt.stubs.suite", typeSpec)

                code shouldContain "@Deprecated"
                code shouldContain "class TestAnnotatedClass"
            }

            test("should throw stub exceptions in method bodies") {
                val classInfo = scanResult.getClassInfo("io.github.hytalekt.stubs.suite.TestBasicClass")
                classInfo.shouldNotBeNull()

                val builder = ClassTypeSpecBuilder(classInfo)
                val typeSpec = builder.build()

                val code = generateJavaCode("io.github.hytalekt.stubs.suite", typeSpec)

                code shouldContain "throw new GeneratedStubException()"
            }
        }

        context("InterfaceTypeSpecBuilder") {
            test("should build basic interface") {
                val classInfo = scanResult.getClassInfo("io.github.hytalekt.stubs.suite.TestInterface")
                classInfo.shouldNotBeNull()

                val builder = InterfaceTypeSpecBuilder(classInfo)
                val typeSpec = builder.build()

                val code = generateJavaCode("io.github.hytalekt.stubs.suite", typeSpec)

                code shouldContain "public interface TestInterface"
                code shouldContain "void basicMethod()"
                code shouldContain "String methodWithReturn()"
                code shouldContain "default String defaultMethod()"
            }

            test("should build generic interface") {
                val classInfo = scanResult.getClassInfo("io.github.hytalekt.stubs.suite.TestInterfaceWithGenerics")
                classInfo.shouldNotBeNull()

                val builder = InterfaceTypeSpecBuilder(classInfo)
                val typeSpec = builder.build()

                val code = generateJavaCode("io.github.hytalekt.stubs.suite", typeSpec)

                code shouldContain "public interface TestInterfaceWithGenerics<T, U extends Number>"
            }

            test("should have method bodies only for default methods") {
                val classInfo = scanResult.getClassInfo("io.github.hytalekt.stubs.suite.TestInterface")
                classInfo.shouldNotBeNull()

                val builder = InterfaceTypeSpecBuilder(classInfo)
                val typeSpec = builder.build()

                val code = generateJavaCode("io.github.hytalekt.stubs.suite", typeSpec)

                // Default methods should have bodies
                code shouldContain "default String defaultMethod()"
                code shouldContain "throw new GeneratedStubException()"
                // Regular interface methods should not have bodies
                code shouldContain "void basicMethod();"
            }
        }

        context("EnumTypeSpecBuilder") {
            test("should build enum with constants") {
                val classInfo = scanResult.getClassInfo("io.github.hytalekt.stubs.suite.TestEnum")
                classInfo.shouldNotBeNull()

                val builder = EnumTypeSpecBuilder(classInfo)
                val typeSpec = builder.build()

                val code = generateJavaCode("io.github.hytalekt.stubs.suite", typeSpec)

                code shouldContain "public enum TestEnum"
                code shouldContain "VALUE_ONE"
                code shouldContain "VALUE_TWO"
                code shouldContain "VALUE_THREE"
            }
        }

        context("RecordTypeSpecBuilder") {
            test("should build record as final class with fields") {
                val classInfo = scanResult.getClassInfo("io.github.hytalekt.stubs.suite.TestRecord")
                classInfo.shouldNotBeNull()

                val builder = RecordTypeSpecBuilder(classInfo)
                val typeSpec = builder.build()

                val code = generateJavaCode("io.github.hytalekt.stubs.suite", typeSpec)

                code shouldContain "public final class TestRecord"
                code shouldContain "private final String name"
                code shouldContain "private final int age"
                code shouldContain "private final double salary"
                code shouldContain "public String getInfo()"
            }
        }

        context("AnnotationTypeSpecBuilder") {
            test("should build annotation with members") {
                val classInfo = scanResult.getClassInfo("io.github.hytalekt.stubs.suite.TestAnnotation")
                classInfo.shouldNotBeNull()

                val builder = AnnotationTypeSpecBuilder(classInfo)
                val typeSpec = builder.build()

                val code = generateJavaCode("io.github.hytalekt.stubs.suite", typeSpec)

                code shouldContain "@interface TestAnnotation"
                code shouldContain "String value()"
                code shouldContain "int priority()"
                code shouldContain "Class<?> type()"
            }

            test("should have @Retention and @Target annotations") {
                val classInfo = scanResult.getClassInfo("io.github.hytalekt.stubs.suite.TestAnnotation")
                classInfo.shouldNotBeNull()

                val builder = AnnotationTypeSpecBuilder(classInfo)
                val typeSpec = builder.build()

                val code = generateJavaCode("io.github.hytalekt.stubs.suite", typeSpec)

                code shouldContain "@Retention"
                code shouldContain "@Target"
            }
        }

        context("TypeSpecBuilder factory") {
            test("should create ClassTypeSpecBuilder for standard class") {
                val classInfo = scanResult.getClassInfo("io.github.hytalekt.stubs.suite.TestBasicClass")
                classInfo.shouldNotBeNull()

                val builder = TypeSpecBuilder(classInfo)
                builder.shouldBeInstanceOf<ClassTypeSpecBuilder>()
            }

            test("should create InterfaceTypeSpecBuilder for interface") {
                val classInfo = scanResult.getClassInfo("io.github.hytalekt.stubs.suite.TestInterface")
                classInfo.shouldNotBeNull()

                val builder = TypeSpecBuilder(classInfo)
                builder.shouldBeInstanceOf<InterfaceTypeSpecBuilder>()
            }

            test("should create EnumTypeSpecBuilder for enum") {
                val classInfo = scanResult.getClassInfo("io.github.hytalekt.stubs.suite.TestEnum")
                classInfo.shouldNotBeNull()

                val builder = TypeSpecBuilder(classInfo)
                builder.shouldBeInstanceOf<EnumTypeSpecBuilder>()
            }

            test("should create RecordTypeSpecBuilder for record") {
                val classInfo = scanResult.getClassInfo("io.github.hytalekt.stubs.suite.TestRecord")
                classInfo.shouldNotBeNull()

                val builder = TypeSpecBuilder(classInfo)
                builder.shouldBeInstanceOf<RecordTypeSpecBuilder>()
            }

            test("should create AnnotationTypeSpecBuilder for annotation") {
                val classInfo = scanResult.getClassInfo("io.github.hytalekt.stubs.suite.TestAnnotation")
                classInfo.shouldNotBeNull()

                val builder = TypeSpecBuilder(classInfo)
                builder.shouldBeInstanceOf<AnnotationTypeSpecBuilder>()
            }
        }

        context("Inner classes") {
            test("should include inner classes within outer class") {
                val classInfo = scanResult.getClassInfo("io.github.hytalekt.stubs.suite.TestInnerClasses")
                classInfo.shouldNotBeNull()

                val builder = ClassTypeSpecBuilder(classInfo)
                val typeSpec = builder.build()

                val code = generateJavaCode("io.github.hytalekt.stubs.suite", typeSpec)

                code shouldContain "public class TestInnerClasses"
                code shouldContain "public class InnerClass"
                code shouldContain "public static class StaticInnerClass"
            }

            test("should handle protected inner class without duplicate modifiers") {
                val classInfo = scanResult.getClassInfo("io.github.hytalekt.stubs.suite.TestInnerClasses")
                classInfo.shouldNotBeNull()

                val builder = ClassTypeSpecBuilder(classInfo)
                val typeSpec = builder.build()

                val code = generateJavaCode("io.github.hytalekt.stubs.suite", typeSpec)

                // Should have protected class, NOT "public protected class"
                code shouldContain "protected class ProtectedInnerClass"
                code shouldNotContain "public protected"
            }

            test("should not include synthetic outer class parameter in inner class constructors") {
                val classInfo = scanResult.getClassInfo("io.github.hytalekt.stubs.suite.TestInnerClasses")
                classInfo.shouldNotBeNull()

                val builder = ClassTypeSpecBuilder(classInfo)
                val typeSpec = builder.build()

                val code = generateJavaCode("io.github.hytalekt.stubs.suite", typeSpec)

                // InnerClass has no-arg constructor, should not have outer reference param
                code shouldContain "public InnerClass()"
                // Should not contain TestInnerClasses as a constructor parameter
                code shouldNotContain "InnerClass(TestInnerClasses"
            }

            test("should include inner class fields and methods") {
                val classInfo = scanResult.getClassInfo("io.github.hytalekt.stubs.suite.TestInnerClasses")
                classInfo.shouldNotBeNull()

                val builder = ClassTypeSpecBuilder(classInfo)
                val typeSpec = builder.build()

                val code = generateJavaCode("io.github.hytalekt.stubs.suite", typeSpec)

                code shouldContain "private String innerField"
                code shouldContain "public String getInnerValue()"
                code shouldContain "public String getOuterValue()"
            }
        }

        context("End-to-end generation") {
            test("should generate valid Java code for all test classes") {
                scanResult.allClasses
                    .filter { !it.isInnerClass && !it.isAnonymousInnerClass && !it.isPrivate }
                    .forEach { classInfo ->
                        val builder = TypeSpecBuilder(classInfo)
                        val typeSpec = builder.build()
                        val code = generateJavaCode(classInfo.packageName, typeSpec)

                        code.shouldNotBeNull()
                        code shouldContain "package ${classInfo.packageName}"
                    }
            }
        }
    })
