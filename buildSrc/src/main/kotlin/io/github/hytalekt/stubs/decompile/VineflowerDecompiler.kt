package io.github.hytalekt.stubs.decompile

import org.jetbrains.java.decompiler.api.Decompiler
import org.jetbrains.java.decompiler.main.decompiler.DirectoryResultSaver
import org.jetbrains.java.decompiler.main.decompiler.PrintStreamLogger
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences
import java.io.File

/**
 * Wrapper around Vineflower decompiler for decompiling JAR files to Java source.
 */
class VineflowerDecompiler(
    private val outputDir: File,
) {
    /**
     * Decompile a JAR file to Java source files.
     *
     * @param jarFile The JAR file to decompile
     * @param libraries Optional library JARs for better type resolution
     * @return Map of class name to source file path
     */
    fun decompile(
        jarFile: File,
        libraries: List<File> = emptyList(),
    ): Map<String, File> {
        outputDir.mkdirs()

        val builder =
            Decompiler
                .Builder()
                .inputs(jarFile)
                .output(DirectoryResultSaver(outputDir))
                .allowedPrefixes("com/hypixel")
                .logger(PrintStreamLogger(System.out))

        // Add library JARs for better type resolution
        libraries.forEach { lib ->
            builder.libraries(lib)
        }

        val decompiler = builder.build()
        decompiler.decompile()

        // Collect all generated .java files
        return collectDecompiledFiles(outputDir)
    }

    private fun collectDecompiledFiles(dir: File): Map<String, File> {
        val results = mutableMapOf<String, File>()

        dir
            .walkTopDown()
            .filter { it.isFile && it.extension == "java" }
            .forEach { file ->
                // Convert file path to class name
                val relativePath = file.relativeTo(outputDir).path
                val className =
                    relativePath
                        .removeSuffix(".java")
                        .replace(File.separatorChar, '.')
                results[className] = file
            }

        return results
    }
}
