import io.github.hytalekt.stubs.HytaleStubsPlugin
import io.github.hytalekt.stubs.task.GenerateSourcesTask
import org.gradle.kotlin.dsl.register

plugins {
    java
    idea
}

apply<HytaleStubsPlugin>()

repositories {
    mavenCentral()
}

dependencies {
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")
    compileOnly("com.google.code.gson:gson:2.11.0")
    compileOnly("org.mongodb:bson:5.3.1")
    compileOnly("io.netty:netty-all:4.2.9.Final")
    compileOnly("io.netty.incubator:netty-incubator-codec-classes-quic:0.0.74.Final")
    compileOnly("com.google.guava:guava:33.4.0-jre")
    compileOnly("org.slf4j:slf4j-api:2.0.16")
    compileOnly("it.unimi.dsi:fastutil:8.5.15")
    compileOnly("org.joml:joml:1.10.8")
    compileOnly("com.google.flogger:flogger:0.8")
    compileOnly("io.sentry:sentry:8.29.0")
    compileOnly("org.checkerframework:checker-qual:3.48.3")
    compileOnly("net.sf.jopt-simple:jopt-simple:5.0.4")
    compileOnly("org.jline:jline:3.26.3")
    compileOnly("com.nimbusds:nimbus-jose-jwt:9.41.1")
}

sourceSets {
    main {
        java {
            srcDir(layout.buildDirectory.dir("gen/sources"))
        }
    }
}

tasks.register<GenerateSourcesTask>("generateSources") {
    sourceJar.set(layout.projectDirectory.file("HytaleServer.jar"))
    outputDirectory.set(layout.buildDirectory.dir("gen/sources"))

    doLast {
        val genDir =
            layout.buildDirectory
                .dir("gen/sources")
                .get()
                .asFile

        /*
        // Fix files that Vineflower failed to decompile
        val connectedBlocksUtil = genDir.resolve("com/hypixel/hytale/server/core/universe/world/connectedblocks/ConnectedBlocksUtil.java")
        if (connectedBlocksUtil.exists() && connectedBlocksUtil.readText().contains("\$VF: Couldn't be decompiled")) {
            connectedBlocksUtil.writeText(
                """
                |package com.hypixel.hytale.server.core.universe.world.connectedblocks;
                |
                |import io.github.hytalekt.stubs.GeneratedStubException;
                |
                |public class ConnectedBlocksUtil {
                |   public ConnectedBlocksUtil() {
                |      throw new GeneratedStubException();
                |   }
                |
                |   public static class ConnectedBlockResult {
                |      public ConnectedBlockResult() {
                |         throw new GeneratedStubException();
                |      }
                |   }
                |}
                """.trimMargin(),
            )
        }

        // Fix enum constructors with parameters - remove them so Java generates default no-arg
        // Match private constructor with parameters (possibly generic, possibly multiline body)
        val enumConstructorPattern = Regex(
            """\n\s+private\s+(?:<[^>]*(?:<[^>]*>[^>]*)*>\s+)?(\w+)\s*\([^)]+\)\s*\{[\s\S]*?\n\s+\}""",
        )
        // Find all enum names including inner enums (public enum X, public static enum X, static enum X)
        val enumNamePattern = Regex("""(?:public\s+)?(?:static\s+)?enum\s+(\w+)""")
        genDir.walkTopDown()
            .filter { it.isFile && it.extension == "java" }
            .forEach { file ->
                val content = file.readText()
                val enumNames = enumNamePattern.findAll(content).map { it.groupValues[1] }.toSet()
                if (enumNames.isNotEmpty()) {
                    // Remove constructors that match any enum name
                    val fixed = enumConstructorPattern.replace(content) { match ->
                        if (match.groupValues[1] in enumNames) "" else match.value
                    }
                    if (fixed != content) {
                        file.writeText(fixed)
                    }
                }
            }

        // Fix enums with abstract methods - remove the abstract method declaration
        // These enums had per-constant implementations which we stripped
        val abstractMethodPattern = Regex("""\n\s+public abstract [^;]+;""")
        listOf(
            "com/hypixel/hytale/server/worldgen/cave/CaveYawMode.java",
            "com/hypixel/hytale/server/worldgen/util/condition/flag/FlagOperator.java",
            "com/hypixel/hytale/server/core/modules/entitystats/modifier/StaticModifier.java",
            "com/hypixel/hytale/server/core/modules/entitystats/asset/condition/LogicCondition.java",
        ).forEach { path ->
            val file = genDir.resolve(path)
            if (file.exists()) {
                val content = file.readText()
                val fixed = abstractMethodPattern.replace(content, "")
                if (fixed != content) file.writeText(fixed)
            }
        }

        // Fix specific files with known issues
        val worldSettingsCommand = genDir.resolve("com/hypixel/hytale/server/core/universe/world/commands/WorldSettingsCommand.java")
        if (worldSettingsCommand.exists()) {
            var content = worldSettingsCommand.readText()
            // Fix inner class constructors that are missing super() calls
            content = content.replace(
                "public SetSubCommand() {\n            throw new GeneratedStubException();",
                "public SetSubCommand() {\n            super(\"\", \"\");\n            throw new GeneratedStubException();"
            )
            content = content.replace(
                "public ResetSubCommand() {\n            throw new GeneratedStubException();",
                "public ResetSubCommand() {\n            super(\"\", \"\");\n            throw new GeneratedStubException();"
            )
            worldSettingsCommand.writeText(content)
        }

        // Fix HytaleLogger - Context class extends LogContext which needs super call
        // LogContext constructor: LogContext(Level level, boolean wasForced)
        val hytaleLogger = genDir.resolve("com/hypixel/hytale/logger/HytaleLogger.java")
        if (hytaleLogger.exists()) {
            var content = hytaleLogger.readText()
            content = content.replace(
                "private Context(@Nonnull Level level) {\n         throw new GeneratedStubException();",
                "private Context(@Nonnull Level level) {\n         super(level, false);\n         throw new GeneratedStubException();"
            )
            hytaleLogger.writeText(content)
        }

        // Fix CombatActionEvaluator - SelfCombatOptionHolder needs super(option)
        val combatActionEvaluator = genDir.resolve("com/hypixel/hytale/builtin/npccombatactionevaluator/evaluator/CombatActionEvaluator.java")
        if (combatActionEvaluator.exists()) {
            var content = combatActionEvaluator.readText()
            content = content.replace(
                "protected SelfCombatOptionHolder(CombatActionOption option) {\n         throw new GeneratedStubException();",
                "protected SelfCombatOptionHolder(CombatActionOption option) {\n         super(option);\n         throw new GeneratedStubException();"
            )
            combatActionEvaluator.writeText(content)
        }

        // Fix type argument issues - these need generic type fixes
        listOf(
            "com/hypixel/hytale/server/core/inventory/container/ItemContainer.java",
            "com/hypixel/hytale/builtin/path/PrefabPathCollection.java",
            "com/hypixel/hytale/builtin/path/WorldPathData.java",
        ).forEach { path ->
            val file = genDir.resolve(path)
            if (file.exists()) {
                var content = file.readText()
                // Fix raw type references by adding wildcards or removing problematic lines
                content = content.replace("@Nonnull\n   private static final it.unimi.dsi.fastutil.objects.ObjectOpenHashSet<> EMPTY_SET", "@Nonnull\n   private static final it.unimi.dsi.fastutil.objects.ObjectOpenHashSet<?> EMPTY_SET")
                content = content.replace("private static final it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap<> EMPTY_MAP", "private static final it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap<?, ?> EMPTY_MAP")
                file.writeText(content)
            }
        }
    }

         */
    }
}

tasks.compileJava {
    // dependsOn("generateSources")
}

tasks.build {
    // dependsOn("generateSources")
}
