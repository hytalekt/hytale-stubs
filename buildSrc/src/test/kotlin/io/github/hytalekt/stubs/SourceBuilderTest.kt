package io.github.hytalekt.stubs

import io.github.classgraph.ClassGraph
import io.github.hytalekt.stubs.jar.SourceBuilder
import org.junit.Test
import java.io.File

internal class SourceBuilderTest {
    @Test
    fun testAll() {
        val outDir = File("build/test/sources")
        outDir.mkdirs()

        val classGraph =
            ClassGraph()
                .enableClassInfo()
                .enableMethodInfo()
                .enableFieldInfo()
                .enableAnnotationInfo()
                .acceptPackages("io.github.hytalekt.stubs.suite")

        classGraph.scan().use { result ->
            result.allClasses.forEach { clazz ->
                if (clazz.isInnerClass || clazz.isAnonymousInnerClass ||
                    clazz.isPrivate
                ) {
                    return@forEach
                }

                SourceBuilder(clazz.packageName, clazz).createJavaFile().writeTo(outDir)
            }
        }
    }
}
