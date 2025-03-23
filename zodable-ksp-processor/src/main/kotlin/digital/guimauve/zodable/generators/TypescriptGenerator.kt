package digital.guimauve.zodable.generators

import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import digital.guimauve.zodable.config.GeneratorConfig
import digital.guimauve.zodable.config.Import
import java.io.File
import java.io.OutputStreamWriter

class TypescriptGenerator(
    private val env: SymbolProcessorEnvironment,
) : ZodableGenerator() {

    override fun resolveSourceFolder(config: GeneratorConfig): File {
        return config.outputPath.resolve("src")
    }

    override fun resolveDependenciesFile(config: GeneratorConfig): File {
        return config.outputPath.resolve("dependencies.txt")
    }

    override fun resolveIndexFile(sourceFolder: File, config: GeneratorConfig): File {
        return sourceFolder.resolve("index.ts")
    }

    override fun resolveClassFile(sourceFolder: File, packageName: String, name: String): File {
        return sourceFolder.resolve("$packageName/$name.ts")
    }

    override fun generateImports(
        imports: Set<Import>,
        writer: OutputStreamWriter,
        config: GeneratorConfig,
    ) {
        writer.write("import {z} from 'zod'\n")
        imports.forEach { (importName, import, isEternal) ->
            writer.write("import {${importName}Schema} from '${if (!isEternal) "src/" else ""}$import'\n")
        }
    }

    override fun generateIndexExport(
        name: String,
        packageName: String,
        writer: OutputStreamWriter,
        config: GeneratorConfig,
    ) {
        writer.write("export * from 'src/$packageName/$name'\n")
    }

    override fun generateClassSchema(
        name: String,
        properties: Set<Pair<String, String>>,
        writer: OutputStreamWriter,
        config: GeneratorConfig,
    ) {
        val body = properties.joinToString(",\n  ") { (name, type) -> "$name: $type" }
        writer.write("\nexport const ${name}Schema = z.object({\n  $body\n})\n")
        generateInferType(name, writer, config)
    }

    override fun generateEnumSchema(
        name: String,
        values: Set<String>,
        writer: OutputStreamWriter,
        config: GeneratorConfig,
    ) {
        val body = values.joinToString(", ") { "\"$it\"" }
        writer.write("export const ${name}Schema = z.enum([\n  $body\n])\n")
        generateInferType(name, writer, config)
    }

    fun generateInferType(
        name: String,
        writer: OutputStreamWriter,
        config: GeneratorConfig,
    ) {
        if (config.inferTypes) writer.write("export type $name = z.infer<typeof ${name}Schema>\n")
    }

    override fun resolveZodType(
        prop: KSPropertyDeclaration,
        config: GeneratorConfig,
    ): Pair<String, List<Import>> {
        val customZodType = prop.annotations.firstOrNull {
            it.shortName.asString() == "ZodType"
        }?.arguments?.firstOrNull()?.value as? String
        if (customZodType != null) return Pair(customZodType, emptyList())
        return resolveZodType(prop.type.resolve(), config)
    }

    private fun resolveZodType(type: KSType, config: GeneratorConfig): Pair<String, List<Import>> {
        val isNullable = type.isMarkedNullable
        val imports = mutableListOf<Import>()
        val zodType = when (type.declaration.qualifiedName?.asString()) {
            "kotlin.String" -> "z.string()"
            "kotlin.Int" -> "z.number().int()"
            "kotlin.Long", "kotlin.Double", "kotlin.Float" -> "z.number()"
            "kotlin.Boolean" -> "z.boolean()"
            "kotlinx.datetime.Instant" -> "z.coerce.date()"
            "dev.kaccelero.models.UUID" -> "z.string().uuid()"
            "kotlin.collections.List" -> {
                val typeArg = type.arguments.firstOrNull()?.type?.resolve()
                if (typeArg != null) {
                    val (innerType, innerImports) = resolveZodType(typeArg, config)
                    imports.addAll(innerImports)
                    "z.array($innerType)"
                } else "z.array(z.unknown())"
            }

            "kotlin.collections.Map" -> {
                val keyType = type.arguments.getOrNull(0)?.type?.resolve()
                val valueType = type.arguments.getOrNull(1)?.type?.resolve()
                if (keyType != null && valueType != null) {
                    val (keyType, keyImports) = resolveZodType(keyType, config)
                    val (valueType, valueImports) = resolveZodType(valueType, config)
                    val coercedKeyType =
                        if (config.coerceMapKeys && keyType.startsWith("z.")) keyType.replaceFirst("z.", "z.coerce.")
                        else keyType
                    imports.addAll(keyImports)
                    imports.addAll(valueImports)
                    "z.record($coercedKeyType, $valueType)"
                } else "z.record(z.string(), z.unknown())"
            }

            else -> {
                val classDeclaration = type.declaration as? KSClassDeclaration
                if (classDeclaration != null && classDeclaration.annotations.any { it.shortName.asString() == "Zodable" }) {
                    val import = classDeclaration.packageName.asString()
                        .replace(".", "/") + "/" + classDeclaration.simpleName.asString()
                    imports += Import(import.split("/").last(), import)
                    "${classDeclaration.simpleName.asString()}Schema"
                } else {
                    env.logger.warn("Unsupported type ${type.declaration.simpleName}, using z.unknown()")
                    "z.unknown()"
                }
            }
        }
        return Pair(if (isNullable) "$zodType${config.optionals}" else zodType, imports)
    }

}
