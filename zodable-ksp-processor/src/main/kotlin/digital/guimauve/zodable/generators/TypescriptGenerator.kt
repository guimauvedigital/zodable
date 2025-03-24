package digital.guimauve.zodable.generators

import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import digital.guimauve.zodable.config.GeneratorConfig
import digital.guimauve.zodable.config.Import
import java.io.File

class TypescriptGenerator(
    env: SymbolProcessorEnvironment,
    config: GeneratorConfig,
) : ZodableGenerator(env, config) {

    override fun shouldKeepAnnotation(annotation: String, filter: String): Boolean {
        return listOf("*", "zod", "ts", "typescript").contains(filter)
    }

    override fun resolveSourceFolder(): File {
        return config.outputPath.resolve("src")
    }

    override fun resolveDependenciesFile(): File {
        return config.outputPath.resolve("dependencies.txt")
    }

    override fun resolveIndexFile(sourceFolder: File): File {
        return sourceFolder.resolve("index.ts")
    }

    override fun resolveClassFile(sourceFolder: File, packageName: String, name: String): File {
        return sourceFolder.resolve("$packageName/$name.ts")
    }

    override fun generateImports(sourceFolder: File, currentFile: File, imports: Set<Import>): String {
        return (listOf("import {z} from \"zod\"") + imports.map { import ->
            val source =
                if (import.isExternal) import.source
                else sourceFolder.resolve(import.source)
                    .relativeTo(currentFile.parentFile) // Folder containing current file
                    .path.let { if (!it.startsWith(".")) "./$it" else it }
            "import {${import.name}Schema} from \"$source\""
        }).joinToString("\n")
    }

    override fun generateIndexExport(name: String, packageName: String): String {
        return "export * from \"./$packageName/$name\""
    }

    override fun generateClassSchema(name: String, properties: Set<Pair<String, String>>): String {
        val body = properties.joinToString(",\n    ") { (name, type) -> "$name: $type" }
        return "export const ${name}Schema = z.object({\n    $body\n})" + generateInferType(name)
    }

    override fun generateEnumSchema(name: String, values: Set<String>): String {
        val body = values.joinToString(", ") { "\"$it\"" }
        return "export const ${name}Schema = z.enum([\n    $body\n])" + generateInferType(name)
    }

    fun generateInferType(name: String): String {
        return if (config.inferTypes) "\nexport type $name = z.infer<typeof ${name}Schema>" else ""
    }

    override fun resolvePrimitiveType(kotlinType: String): String? {
        return when (kotlinType) {
            "kotlin.String" -> "z.string()"
            "kotlin.Int" -> "z.number().int()"
            "kotlin.Long", "kotlin.Double", "kotlin.Float" -> "z.number()"
            "kotlin.Boolean" -> "z.boolean()"
            "kotlinx.datetime.Instant" -> "z.coerce.date()"
            "dev.kaccelero.models.UUID" -> "z.string().uuid()"
            "kotlin.collections.List" -> "z.array()"
            "kotlin.collections.Map" -> "z.record()"
            else -> null
        }
    }

    override fun resolveZodableType(name: String): String {
        return "${name}Schema"
    }

    override fun resolveUnknownType(): String {
        return "z.unknown()"
    }

    override fun addGenericArguments(type: String, arguments: List<String>): String {
        if (!type.endsWith("()")) return type

        // Coerce arguments if needed
        val coercedArguments = when (type) {
            // For map, we need to coerce the key type (always from string inside json)
            "z.record()" -> {
                val firstArgument = arguments.first()
                val coercedFirstArgument =
                    if (config.coerceMapKeys && firstArgument.startsWith("z."))
                        firstArgument.replaceFirst("z.", "z.coerce.")
                    else firstArgument
                listOf(coercedFirstArgument) + arguments.drop(1)
            }

            else -> arguments
        }
        return type.substring(0, type.length - 2) + "(${coercedArguments.joinToString(", ")})"
    }

    override fun markAsNullable(type: String): String {
        return "$type${config.optionals}"
    }

}
