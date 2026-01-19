package io.github.hytalekt.stubs.task

import com.github.javaparser.JavaParser
import io.github.hytalekt.stubs.postprocess.ConstructorSignatureIndex
import io.github.hytalekt.stubs.postprocess.StubPostProcessor
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.*
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault
abstract class ProcessStubsTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputJar: RegularFileProperty

    @get:OutputDirectory
    abstract val inputDirectory: DirectoryProperty

    /**
     * Optional directory containing manual patch files.
     * Files in this directory will be copied over the generated sources after processing.
     * Directory structure should mirror the package structure (e.g., patches/com/example/MyClass.java)
     */
    @get:InputDirectory
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val patchesDirectory: DirectoryProperty

    init {
        group = "stubs"
        description = "Converts Java class files to stubs"
    }

    @TaskAction
    fun generateSources() {
        logger.lifecycle("Post-processing decompiled sources into stubs...")

        val jarFile = inputJar.get().asFile
        val signatureIndex = ConstructorSignatureIndex(jarFile)

        StubPostProcessor(JavaParser(), signatureIndex).processDirectory(inputDirectory.get().asFile)

        // Apply manual patches if directory exists
        if (patchesDirectory.isPresent) {
            val patchDir = patchesDirectory.get().asFile
            if (patchDir.exists() && patchDir.isDirectory) {
                applyPatches(patchDir, inputDirectory.get().asFile)
            }
        }

        logger.lifecycle("Post-processing complete")
    }

    private fun applyPatches(
        patchDir: File,
        outputDir: File,
    ) {
        var patchCount = 0
        patchDir
            .walkTopDown()
            .filter { it.isFile && it.extension == "java" }
            .forEach { patchFile ->
                val relativePath = patchFile.relativeTo(patchDir).path
                val targetFile = File(outputDir, relativePath)

                targetFile.parentFile.mkdirs()
                patchFile.copyTo(targetFile, overwrite = true)
                logger.lifecycle("Applied patch: $relativePath")
                patchCount++
            }

        if (patchCount > 0) {
            logger.lifecycle("Applied $patchCount manual patch(es)")
        }
    }
}
