package digital.guimauve.zodable.generators

import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import digital.guimauve.zodable.config.GeneratorConfig
import digital.guimauve.zodable.config.Import
import java.io.File

class PythonGenerator(
    env: SymbolProcessorEnvironment,
    config: GeneratorConfig,
) : ZodableGenerator(env, config) {

    fun String.pythonCompatible() = this.replace("-", "_")

    override fun shouldKeepAnnotation(annotation: String, filter: String): Boolean {
        return listOf("*", "pydantic", "py", "python").contains(filter)
    }

    override fun resolveSourceFolder(): File {
        return config.outputPath.resolve("src/" + config.packageName.pythonCompatible())
    }

    override fun resolveDependenciesFile(): File {
        return config.outputPath.resolve("requirements.txt")
    }

    override fun resolveIndexFile(sourceFolder: File): File {
        return sourceFolder.resolve("__init__.py")
    }

    override fun resolveClassFile(sourceFolder: File, packageName: String, name: String): File {
        return sourceFolder.resolve("$packageName/$name.py")
    }

    override fun generateImports(sourceFolder: File, currentFile: File, imports: Set<Import>): String {
        return (listOf(
            "from pydantic import BaseModel",
            "from enum import Enum",
        ) + imports.map { import ->
            val source =
                if (import.isExternal) import.source.pythonCompatible()
                else sourceFolder.resolve(import.source)
                    .relativeTo(config.outputPath.resolve("src"))
                    .path.replace("/", ".")
            "from $source import ${import.name}${if (!import.isInvariable) "Schema" else ""}"
        }).joinToString("\n")
    }

    override fun generateIndexExport(name: String, packageName: String): String {
        val source = "${config.packageName.pythonCompatible()}.${packageName.replace("/", ".")}.$name"
        return "from $source import ${name}Schema"
    }

    override fun generateClassSchema(name: String, properties: Set<Pair<String, String>>): String {
        return "class ${name}Schema(BaseModel):\n" + properties.joinToString("\n") { (name, type) -> "    $name: $type" }
    }

    override fun generateEnumSchema(name: String, values: Set<String>): String {
        return "class ${name}Schema(str, Enum):\n" + values.joinToString("\n") { name -> "    $name = '$name'" }
    }

    override fun resolvePrimitiveType(kotlinType: String): Pair<String, List<Import>>? {
        return when (kotlinType) {
            "kotlin.String" -> "str" to emptyList()
            "kotlin.Int", "kotlin.Long" -> "int" to emptyList()
            "kotlin.Double", "kotlin.Float" -> "float" to emptyList()
            "kotlin.Boolean" -> "bool" to emptyList()
            "kotlinx.datetime.Instant" -> "datetime" to listOf(
                Import("datetime", "datetime", isExternal = true, isInvariable = true)
            )

            "dev.kaccelero.models.UUID" -> "UUID" to listOf(
                Import("UUID", "uuid", isExternal = true, isInvariable = true)
            )

            "kotlin.collections.List" -> "list[]" to emptyList()
            "kotlin.collections.Map" -> "dict[]" to emptyList()
            else -> null
        }
    }

    override fun resolveZodableType(name: String): Pair<String, List<Import>> {
        return "${name}Schema" to emptyList()
    }

    override fun resolveUnknownType(): Pair<String, List<Import>> {
        return "Any" to listOf(Import("Any", "typing", isExternal = true, isInvariable = true))
    }

    override fun addGenericArguments(type: String, arguments: List<String>): String {
        if (!type.endsWith("[]")) return type
        return type.substring(0, type.length - 2) + "[${arguments.joinToString(", ")}]"
    }

    override fun markAsNullable(type: String): String {
        return "$type | None"
    }

}
