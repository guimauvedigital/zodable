package digital.guimauve.zodable

import com.google.devtools.ksp.gradle.KspExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Exec
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register
import java.io.File

abstract class ZodablePlugin : Plugin<Project> {

    private val zodableVersion = "1.0.1"

    override fun apply(project: Project) {
        val outputPath = project.file("build/zodable")

        project.pluginManager.apply("com.google.devtools.ksp")
        project.configureKspProcessor(outputPath)
        project.afterEvaluate {
            project.configureTasks(outputPath)
        }
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
        val kspConfig = getKspConfig()

        dependencies {
            //add(kspConfig.apiConfigurationName, project(":zodable-annotations"))
            //add(kspConfig.kspConfigurationName, project(":zodable-ksp-processor"))
            add(kspConfig.apiConfigurationName, "digital.guimauve.zodable:zodable-annotations:$zodableVersion")
            add(kspConfig.kspConfigurationName, "digital.guimauve.zodable:zodable-ksp-processor:$zodableVersion")
        }

        plugins.withId("com.google.devtools.ksp") {
            extensions.getByType<KspExtension>().apply {
                arg("zodableOutputPath", outputPath.absolutePath)
            }
        }
    }

    private fun Project.configureTasks(outputPath: File) {
        val kspConfig = getKspConfig()

        val setupZodablePackage = tasks.register<Exec>("setupZodablePackage") {
            group = "build"
            description = "Setup zodable npm package"

            workingDir = outputPath
            commandLine = listOf("npm", "init", "-y")

            dependsOn(kspConfig.taskName)

            doLast {
                exec {
                    workingDir = outputPath
                    commandLine = listOf("npm", "pkg", "set", "name=${project.name}")
                }
                exec {
                    workingDir = outputPath
                    commandLine = listOf("npm", "pkg", "set", "version=${project.version}")
                }
                exec {
                    workingDir = outputPath
                    commandLine = listOf("npm", "pkg", "set", "main=schemas.js")
                }
                exec {
                    workingDir = outputPath
                    commandLine = listOf("npm", "pkg", "set", "types=schemas.d.ts")
                }
                exec {
                    workingDir = outputPath
                    commandLine = listOf("npm", "install", "typescript", "--save-dev")
                }
                exec {
                    workingDir = outputPath
                    commandLine = listOf("npm", "install", "zod")
                }
                exec {
                    workingDir = outputPath
                    commandLine = listOf("npx", "tsc", "--init", "-d")
                }
                exec {
                    workingDir = outputPath
                    commandLine = listOf("npx", "tsc")
                }
            }
        }
        tasks.named("build").configure {
            dependsOn(setupZodablePackage)
        }
    }

}
