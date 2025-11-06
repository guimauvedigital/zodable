package digital.guimauve.zodable

import com.google.devtools.ksp.gradle.KspExtension
import digital.guimauve.zodable.Files.pythonCompatible
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

    private val zodableVersion = "1.6.2"

    override fun apply(project: Project) {
        val outputPath = project.file("build/zodable")

        project.pluginManager.apply("com.google.devtools.ksp")
        project.configureExtensions()
        project.configureKspProcessor()
        project.afterEvaluate {
            project.configureKspArgs(outputPath)
            project.configureTasks(outputPath)
        }
    }

    private fun Project.configureExtensions() {
        val extension = extensions.create<ZodableExtension>("zodable")
        extension.enableTypescript.convention(true)
        extension.enablePython.convention(false)
        extension.inferTypes.convention(true)
        extension.coerceMapKeys.convention(true)
        extension.optionals.convention(Optionals.NULLISH)
        extension.packageName.convention(project.name)
        extension.packageVersion.convention(project.version.toString())
        extension.externalPackageInstallCommands.convention(emptyMap())
        extension.externalPackageLocations.convention(emptyMap())
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

    private fun Project.configureKspProcessor() {
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
    }

    private fun Project.configureKspArgs(outputPath: File) {
        val extension = extensions.getByType<ZodableExtension>()

        plugins.withId("com.google.devtools.ksp") {
            extensions.getByType<KspExtension>().apply {
                arg("zodableEnableTypescript", extension.enableTypescript.get().toString())
                arg("zodableEnablePython", extension.enablePython.get().toString())
                arg("zodablePackageName", extension.packageName.get())
                arg("zodableOutputPath", outputPath.absolutePath)
                arg("zodableInferTypes", extension.inferTypes.get().toString())
                arg("zodableCoerceMapKeys", extension.coerceMapKeys.get().toString())
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
                buildList {
                    add(ExecCommand(listOf("npm", "pkg", "set", "name=${extension.packageName.get()}")))
                    add(ExecCommand(listOf("npm", "pkg", "set", "version=${extension.packageVersion.get()}")))
                    add(ExecCommand(listOf("npm", "pkg", "set", "description=${Files.ZOD_DESCRIPTION}")))
                    add(ExecCommand(listOf("npm", "pkg", "set", "main=src/index.js")))
                    add(ExecCommand(listOf("npm", "pkg", "set", "types=src/index.d.ts")))
                    add(ExecCommand(listOf("npm", "pkg", "set", "files[0]=src/**/*")))
                    add(ExecCommand(listOf("npm", "install", "typescript", "--save-dev")))
                    File(outputPath, "dependencies.txt").readLines().forEach { dep ->
                        val npmPackage = extension.externalPackageLocations.get()[dep] ?: dep
                        val installCommand = extension.externalPackageInstallCommands.get()[dep] ?: listOf("npm", "install")
                        add(ExecCommand(installCommand + npmPackage))
                    }
                    add(ExecCommand(
                        listOf(
                            "npx", "tsc", "--init",
                            "-d",
                            "--baseUrl", "./",
                            "--isolatedModules", "false",
                            "--verbatimModuleSyntax", "false"
                        )
                    ))
                    add(ExecCommand(listOf("npx", "tsc")))
                }.forEach { command ->
                    exec {
                        workingDir = outputPath
                        commandLine = command.commandLine
                        command.standardInput?.let {
                            standardInput = outputPath.resolve(it).inputStream()
                        }
                    }
                }
            }
        }
        val setupPydantablePackage = tasks.register<Exec>("setupPydantablePackage") {
            val pythonOutputPath = outputPath.parentFile.resolve("pydantable")
            val venvPath = pythonOutputPath.resolve(".venv")
            val pythonExec = venvPath.resolve("bin/python").absolutePath
            val pipExec = venvPath.resolve("bin/pip").absolutePath

            group = "build"
            description = "Setup zodable pypi package"

            workingDir = pythonOutputPath
            commandLine = listOf("python3", "-m", "venv", ".venv")

            dependsOn(kspConfig.taskName)
            doLast {
                listOf(
                    ExecCommand(listOf(pipExec, "install", "-r", "requirements.txt")),
                    ExecCommand(listOf(pipExec, "install", "toml")),
                    ExecCommand(
                        listOf(
                            pythonExec, "-c", Files.generatePyProjectToml(
                                extension.packageName.get(),
                                extension.packageVersion.get(),
                            )
                        )
                    ),
                    ExecCommand(listOf("touch", "${extension.packageName.get().pythonCompatible()}/py.typed")),
                    ExecCommand(listOf(pipExec, "install", "mypy", "build", "twine")),
                    ExecCommand(listOf(pythonExec, "-m", "mypy", extension.packageName.get().pythonCompatible())),
                    ExecCommand(listOf(pythonExec, "-m", "build")),
                ).forEach { command ->
                    exec {
                        workingDir = pythonOutputPath
                        commandLine = command.commandLine
                    }
                }
            }
        }
        tasks.named("build").configure {
            if (extension.enableTypescript.get()) dependsOn(setupZodablePackage)
            if (extension.enablePython.get()) dependsOn(setupPydantablePackage)
        }
    }

}
