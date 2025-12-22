@file:Suppress("ktlint:standard:no-wildcard-imports")

package io.github.hytalekt.stubs.task

import io.github.classgraph.ClassGraph
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.*

abstract class GenerateSourcesTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceJar: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val docSourceSet: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    init {
        group = "documentation"
        description = "Generates sources from a JAR and doc sources"
    }

    @TaskAction
    fun generateSources() {
        val outDir = outputDirectory.get().asFile
        outDir.mkdirs()

        val classGraph =
            ClassGraph()
                .enableClassInfo()
                .enableMethodInfo()
                .enableFieldInfo()
                .enableAnnotationInfo()
                .overrideClasspath(sourceJar.get().asFile)

        classGraph.scan().use { result ->
            result.allClasses.forEach { clazz ->
                if (clazz.isInnerClass || clazz.isAnonymousInnerClass ||
                    clazz.isPrivate
                ) {
                    return@forEach
                }

                TODO("use spec builders")
            }
        }
    }
}
