package io.github.hytalekt.stubs

import com.palantir.javapoet.JavaFile
import io.github.classgraph.ClassGraph
import io.github.hytalekt.stubs.spec.TypeSpecBuilder
import io.kotest.core.spec.style.FunSpec
import java.io.File

/**
 * Debug test that generates stub code from test suite classes.
 * Output is written to ./debug/stubs for manual inspection.
 *
 * Run with: ./gradlew :buildSrc:test --tests "DebugStubGenerationTest"
 */
class DebugStubGenerationTest : FunSpec({

    test("generate stubs from test suite to ./debug/stubs") {
        val outputDir = File("debug/stubs")

        // Clean and create output directory
        if (outputDir.exists()) {
            outputDir.deleteRecursively()
        }
        outputDir.mkdirs()

        // Scan test suite classes
        ClassGraph()
            .acceptPackages("io.github.hytalekt.stubs.suite")
            .enableClassInfo()
            .enableMethodInfo()
            .enableFieldInfo()
            .enableAnnotationInfo()
            .enableStaticFinalFieldConstantInitializerValues()
            .ignoreClassVisibility()
            .ignoreMethodVisibility()
            .scan()
            .use { result ->
                result.allClasses.forEach { classInfo ->
                    // Skip inner/anonymous/private classes for cleaner output
                    if (classInfo.isInnerClass || classInfo.isAnonymousInnerClass || classInfo.isPrivate) {
                        return@forEach
                    }

                    try {
                        // Build TypeSpec for this class
                        val builder = TypeSpecBuilder(classInfo)
                        val typeSpec = builder.build()

                        // Get package name
                        val packageName = classInfo.packageName

                        // Generate Java file
                        val javaFile = JavaFile.builder(packageName, typeSpec)
                            .addFileComment("Generated stub for testing - Parameter names should be preserved!")
                            .build()

                        // Write to output directory
                        javaFile.writeTo(outputDir)

                        println("✓ Generated: ${classInfo.simpleName}.java")
                    } catch (e: Exception) {
                        println("✗ Failed to generate ${classInfo.simpleName}: ${e.message}")
                        e.printStackTrace()
                    }
                }
            }

        println("\n✨ Stub generation complete!")
        println("📁 Output directory: ${outputDir.absolutePath}")
        println("\n💡 Check the generated files to verify parameter names are preserved.")
        println("   Look for methods like: setValue(String value) instead of setValue(String param0)")
    }
})
