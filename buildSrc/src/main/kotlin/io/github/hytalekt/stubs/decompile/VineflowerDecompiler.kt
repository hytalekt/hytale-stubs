package io.github.hytalekt.stubs.decompile

import org.jetbrains.java.decompiler.api.Decompiler
import org.jetbrains.java.decompiler.main.decompiler.DirectoryResultSaver
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences
import java.io.File

/**
 * Wrapper around Vineflower decompiler for decompiling JAR files to Java source.
 */
class VineflowerDecompiler(
    private val outputDir: File,
    private val logger: DecompilerLogger? = null,
) {
    /**
     * Decompile a JAR file to Java source files.
     *
     * @param jarFile The JAR file to decompile
     * @param libraries Optional library JARs for better type resolution
     * @return Map of class name to source file path
     */
    fun decompile(jarFile: File, libraries: List<File> = emptyList()): Map<String, File> {
        outputDir.mkdirs()

        val decompilerLogger = logger?.let { VineflowerLoggerAdapter(it) }
            ?: VineflowerLoggerAdapter(object : DecompilerLogger {
                override fun log(level: LogLevel, message: String) {
                    if (level == LogLevel.ERROR) {
                        System.err.println("[Vineflower] $message")
                    }
                }
            })

        val builder = Decompiler.Builder()
            .inputs(jarFile)
            .output(DirectoryResultSaver(outputDir))
            .logger(decompilerLogger)
            // Configure for best output quality
            .option(IFernflowerPreferences.DECOMPILE_GENERIC_SIGNATURES, "1")
            .option(IFernflowerPreferences.REMOVE_SYNTHETIC, "1")
            .option(IFernflowerPreferences.REMOVE_BRIDGE, "1")
            .option(IFernflowerPreferences.INCLUDE_ENTIRE_CLASSPATH, "0")
            .option(IFernflowerPreferences.ASCII_STRING_CHARACTERS, "0")
            .option(IFernflowerPreferences.BYTECODE_SOURCE_MAPPING, "0")
            .option(IFernflowerPreferences.DUMP_CODE_LINES, "0")
            .option(IFernflowerPreferences.INDENT_STRING, "    ")
            .option(IFernflowerPreferences.NEW_LINE_SEPARATOR, "1")

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

        dir.walkTopDown()
            .filter { it.isFile && it.extension == "java" }
            .forEach { file ->
                // Convert file path to class name
                val relativePath = file.relativeTo(outputDir).path
                val className = relativePath
                    .removeSuffix(".java")
                    .replace(File.separatorChar, '.')
                results[className] = file
            }

        return results
    }
}

interface DecompilerLogger {
    fun log(level: LogLevel, message: String)
}

enum class LogLevel {
    DEBUG, INFO, WARN, ERROR
}

private class VineflowerLoggerAdapter(
    private val logger: DecompilerLogger,
) : IFernflowerLogger() {
    override fun writeMessage(message: String?, severity: Severity?) {
        if (message == null) return
        val level = when (severity) {
            Severity.TRACE -> LogLevel.DEBUG
            Severity.INFO -> LogLevel.INFO
            Severity.WARN -> LogLevel.WARN
            Severity.ERROR -> LogLevel.ERROR
            null -> LogLevel.INFO
        }
        logger.log(level, message)
    }

    override fun writeMessage(message: String?, severity: Severity?, t: Throwable?) {
        writeMessage(message, severity)
        if (t != null) {
            logger.log(LogLevel.ERROR, t.stackTraceToString())
        }
    }
}
