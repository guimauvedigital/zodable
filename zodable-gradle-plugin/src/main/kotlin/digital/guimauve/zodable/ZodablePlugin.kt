package digital.guimauve.zodable

import com.google.devtools.ksp.gradle.KspExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Exec
import org.gradle.kotlin.dsl.register
import java.io.File

abstract class ZodablePlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val outputPath = project.file("build/zodable")
        project.configureKspProcessor(outputPath)
        project.configureTasks(outputPath)
    }

    private fun Project.configureKspProcessor(outputPath: File) {
        pluginManager.apply("com.google.devtools.ksp")
        dependencies.add("implementation", dependencies.create(project(":zodable-ksp-processor")))
        dependencies.add("ksp", dependencies.create(project(":zodable-ksp-processor")))

        plugins.withId("com.google.devtools.ksp") {
            val kspExtension = extensions.getByType(KspExtension::class.java)
            kspExtension.arg("outputPath", outputPath.absolutePath)
        }
    }

    private fun Project.configureTasks(outputPath: File) {
        val setupZodablePackage = tasks.register<Exec>("setupZodablePackage") {
            group = "build"
            description = "Setup zodable npm package"

            workingDir = outputPath
            commandLine = listOf("npm", "init", "-y")

            dependsOn("kspKotlin")

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
