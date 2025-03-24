package digital.guimauve.zodable.generators

import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import digital.guimauve.zodable.config.GeneratorConfig
import digital.guimauve.zodable.config.Import
import java.io.File
import java.io.OutputStreamWriter

class TypescriptGenerator(
    env: SymbolProcessorEnvironment,
    config: GeneratorConfig,
) : ZodableGenerator(env, config) {

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

    override fun generateImports(imports: Set<Import>, writer: OutputStreamWriter) {
        writer.write("import {z} from 'zod'\n")
        imports.forEach { (importName, import, isEternal) ->
            writer.write("import {${importName}Schema} from '${if (!isEternal) "src/" else ""}$import'\n")
        }
    }

    override fun generateIndexExport(name: String, packageName: String, writer: OutputStreamWriter) {
        writer.write("export * from 'src/$packageName/$name'\n")
    }

    override fun generateClassSchema(name: String, properties: Set<Pair<String, String>>, writer: OutputStreamWriter) {
        val body = properties.joinToString(",\n  ") { (name, type) -> "$name: $type" }
        writer.write("export const ${name}Schema = z.object({\n  $body\n})\n")
        generateInferType(name, writer)
    }

    override fun generateEnumSchema(name: String, values: Set<String>, writer: OutputStreamWriter) {
        val body = values.joinToString(", ") { "\"$it\"" }
        writer.write("export const ${name}Schema = z.enum([\n  $body\n])\n")
        generateInferType(name, writer)
    }

    fun generateInferType(name: String, writer: OutputStreamWriter) {
        if (config.inferTypes) writer.write("export type $name = z.infer<typeof ${name}Schema>\n")
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
        return if (type.endsWith("()")) {
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
            type.substring(0, type.length - 2) + "(${coercedArguments.joinToString(", ")})"
        } else type
    }

    override fun markAsNullable(
        type: String,
    ): String {
        return "$type${config.optionals}"
    }

}
