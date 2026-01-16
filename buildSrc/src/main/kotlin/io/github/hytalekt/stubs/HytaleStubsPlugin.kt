package io.github.hytalekt.stubs

import io.github.hytalekt.stubs.vineflower.ExamplePluginSource
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.gradle.plugins.ide.idea.IdeaPlugin
import org.gradle.plugins.ide.idea.model.IdeaModel
import org.jetbrains.java.decompiler.main.plugins.PluginSources

class HytaleStubsPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.plugins.apply("java")

        // Clear any previously registered sources to avoid duplicates from Gradle daemon
        PluginSources.PLUGIN_SOURCES.removeIf { it is ExamplePluginSource }
        PluginSources.PLUGIN_SOURCES.add(ExamplePluginSource())

        val sourceSets = project.extensions.getByType<SourceSetContainer>()

        sourceSets.forEach { sourceSet ->
            val baseSourceDirectorySet =
                project.objects.sourceDirectorySet(
                    "${sourceSet.name}Docs",
                    "Docs for ${sourceSet.name}",
                )

            val docs =
                project.objects.newInstance(
                    DefaultDocSourceDirectorySet::class.java,
                    baseSourceDirectorySet,
                )

            docs.filter.include("**/*.json")
            docs.srcDirs("src/${sourceSet.name}/docs")

            sourceSet.extensions.add(
                DocSourceDirectorySet::class.java,
                "docs",
                docs,
            )
        }

        project.plugins.withType<IdeaPlugin> {
            project.extensions.findByType<IdeaModel>()?.apply {
                module {
                    sourceSets.forEach { sourceSet ->
                        val docs = sourceSet.docs

                        if (sourceSet.name.contains("test", ignoreCase = true)) {
                            testSources.from(*(docs.srcDirs.toTypedArray()))
                        } else {
                            println("")
                            sourceDirs.addAll(docs.srcDirs)
                        }
                    }
                }
            }
        }
    }
}

val SourceSet.docs: DocSourceDirectorySet
    get() = extensions.getByType<DocSourceDirectorySet>()

fun SourceSet.docs(action: Action<DocSourceDirectorySet>) = extensions.getByType<DocSourceDirectorySet>().apply(action::execute)
