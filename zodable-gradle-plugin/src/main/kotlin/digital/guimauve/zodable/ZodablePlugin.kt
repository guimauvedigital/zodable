package digital.guimauve.zodable

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Exec
import org.gradle.kotlin.dsl.register

abstract class ZodablePlugin : Plugin<Project> {

    override fun apply(project: Project) {
        project.configureKspProcessor()
        project.configureTasks()
    }

    private fun Project.configureKspProcessor() {
        // TODO: Fix this
        project.pluginManager.apply("com.google.devtools.ksp")
        project.dependencies.add("ksp", project.dependencies.create(project(":zodable-ksp-processor")))
    }

    private fun Project.configureTasks() {
        val compileTypeScript = project.tasks.register<Exec>("compileTypeScript") {
            group = "build"
            description = "Compile TypeScript schemas using tsc"

            workingDir = project.file("build/generated/zod-schemas")
            commandLine = listOf("npx", "tsc")

            dependsOn("kspKotlin")
        }

        project.tasks.named("build").configure {
            dependsOn(compileTypeScript)
        }
    }

}
