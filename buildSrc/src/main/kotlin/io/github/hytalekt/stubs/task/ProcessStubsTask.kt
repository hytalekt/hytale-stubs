package io.github.hytalekt.stubs.task

import com.github.javaparser.JavaParser
import io.github.hytalekt.stubs.postprocess.StubPostProcessor
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.*
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault
abstract class ProcessStubsTask : DefaultTask() {
    @get:OutputDirectory
    abstract val inputDirectory: DirectoryProperty

    init {
        group = "stubs"
        description = "Converts Java class files to stubs"
    }

    @TaskAction
    fun generateSources() {
        logger.lifecycle("Post-processing decompiled sources into stubs...")
        StubPostProcessor(JavaParser()).processDirectory(inputDirectory.get().asFile)
        logger.lifecycle("Post-processing complete")
    }
}
