package digital.guimauve.zodable

import com.google.devtools.ksp.gradle.KspExtension
import digital.guimauve.zodable.extensions.ZodableExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Exec
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register
import java.io.File

abstract class ZodablePlugin : Plugin<Project> {

    private val zodableVersion = "1.0.1"

    override fun apply(project: Project) {
        val outputPath = project.file("build/zodable")

        project.pluginManager.apply("com.google.devtools.ksp")
        project.configureExtensions()
        project.configureKspProcessor(outputPath)
        project.afterEvaluate {
            project.configureTasks(outputPath)
        }
    }

    private fun Project.configureExtensions() {
        val extension = extensions.create<ZodableExtension>("zodable")
        extension.inferTypes.convention(true)
        extension.optionals.convention(Optionals.NULLISH)
        extension.packageName.convention(project.name)
        extension.packageVersion.convention(project.version.toString())
    }

    private fun Project.getKspConfig(): KspConfig {
        return if (plugins.hasPlugin("org.jetbrains.kotlin.multiplatform")) KspConfig(
            apiConfigurationName = "commonMainApi",
            kspConfigurationName = "kspCommonMainMetadata",
            taskName = "kspCommonMainKotlinMetadata"
        ) else KspConfig(
            apiConfigurationName = "api",
            kspConfigurationName = "ksp",
            taskName = "kspKotlin"
        )
    }

    private fun Project.configureKspProcessor(outputPath: File) {
        val extension = extensions.getByType<ZodableExtension>()
        val kspConfig = getKspConfig()

        dependencies {
            if (project.group == "digital.guimauve.zodable") {
                add(kspConfig.apiConfigurationName, project(":zodable-annotations"))
                add(kspConfig.kspConfigurationName, project(":zodable-ksp-processor"))
            } else {
                add(kspConfig.apiConfigurationName, "digital.guimauve.zodable:zodable-annotations:$zodableVersion")
                add(kspConfig.kspConfigurationName, "digital.guimauve.zodable:zodable-ksp-processor:$zodableVersion")
            }
        }

        plugins.withId("com.google.devtools.ksp") {
            extensions.getByType<KspExtension>().apply {
                arg("zodableOutputPath", outputPath.absolutePath)
                arg("zodableInferTypes", extension.inferTypes.get().toString())
                arg("zodableOptionals", extension.optionals.get().zodType)
            }
        }
    }

    private fun Project.configureTasks(outputPath: File) {
        val extension = extensions.getByType<ZodableExtension>()
        val kspConfig = getKspConfig()

        val setupZodablePackage = tasks.register<Exec>("setupZodablePackage") {
            group = "build"
            description = "Setup zodable npm package"

            workingDir = outputPath
            commandLine = listOf("npm", "init", "-y")

            dependsOn(kspConfig.taskName)
            doLast {
                listOf(
                    listOf("npm", "pkg", "set", "name=${extension.packageName.get()}"),
                    listOf("npm", "pkg", "set", "version=${extension.packageVersion.get()}"),
                    listOf("npm", "pkg", "set", "main=src/index.js"),
                    listOf("npm", "pkg", "set", "types=src/index.d.ts"),
                    listOf("npm", "pkg", "set", "files[0]=src/**/*"),
                    listOf("npm", "install", "typescript", "--save-dev"),
                    listOf("npm", "install", "zod@latest"),
                    listOf("npx", "tsc", "--init", "-d", "--baseUrl", "./"),
                    listOf("npx", "tsc")
                ).forEach { command ->
                    exec {
                        workingDir = outputPath
                        commandLine = command
                    }
                }
            }
        }
        tasks.named("build").configure {
            dependsOn(setupZodablePackage)
        }
    }

}
