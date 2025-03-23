package digital.guimauve.zodable.generators

import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import digital.guimauve.zodable.config.GeneratorConfig
import java.io.OutputStreamWriter

class TypescriptGenerator(
    private val env: SymbolProcessorEnvironment,
) : ZodableGenerator {

    override fun generateFiles(annotatedClasses: Sequence<KSClassDeclaration>, config: GeneratorConfig) {
        val src = config.outputPath.resolve("src").also { it.mkdirs() }
        val importedPackages = mutableSetOf<String>()
        val indexFile = src.resolve("index.ts").outputStream()
        OutputStreamWriter(indexFile, Charsets.UTF_8).use { writer ->
            for (classDeclaration in annotatedClasses) {
                val className = classDeclaration.simpleName.asString()
                val packageName = classDeclaration.packageName.asString().replace(".", "/")
                src.resolve(packageName).mkdirs()
                val file = src.resolve("$packageName/$className.ts").outputStream()
                OutputStreamWriter(file, Charsets.UTF_8).use { schemaWriter ->
                    val imports = mutableSetOf<String>()
                    var properties = ""

                    if (classDeclaration.classKind == ClassKind.ENUM_CLASS) {
                        properties = classDeclaration.declarations.filterIsInstance<KSClassDeclaration>()
                            .map { it.simpleName.asString() }
                            .joinToString(", ") { "\"$it\"" }
                    } else {
                        val propertiesToInclude = classDeclaration.getAllProperties().filter { it.hasBackingField }
                        properties = propertiesToInclude.joinToString(",\n  ") { prop ->
                            val name = prop.simpleName.asString()
                            val (type, localImports) = resolveZodType(prop, config)
                            imports.addAll(localImports)
                            "$name: $type"
                        }
                    }

                    schemaWriter.write("import {z} from 'zod'\n")
                    imports.forEach { import ->
                        val importName = import.split("/").last()
                        schemaWriter.write("import {${importName}Schema} from 'src/$import'\n")
                    }
                    classDeclaration.annotations.forEach { annotation ->
                        if (annotation.shortName.asString() == "ZodImport") {
                            val externalName = annotation.arguments.firstOrNull()?.value as? String
                            val externalPackageName = annotation.arguments.lastOrNull()?.value as? String
                            if (externalName != null && externalPackageName != null) {
                                importedPackages.add(externalPackageName)
                                schemaWriter.write("import {${externalName}Schema} from '$externalPackageName'\n")
                            }
                        }
                    }

                    schemaWriter.write("\nexport const ${className}Schema = ")
                    if (classDeclaration.classKind == ClassKind.ENUM_CLASS) {
                        schemaWriter.write("z.enum([\n  $properties\n])\n")
                    } else {
                        schemaWriter.write("z.object({\n  $properties\n})\n")
                    }
                    if (config.inferTypes) schemaWriter.write("export type $className = z.infer<typeof ${className}Schema>\n")
                }
                writer.write("export * from 'src/$packageName/$className'\n")
            }
        }

        val dependenciesFile = config.outputPath.resolve("dependencies.txt").outputStream()
        OutputStreamWriter(dependenciesFile, Charsets.UTF_8).use { depWriter ->
            importedPackages.forEach { depWriter.write("$it\n") }
        }
    }

    private fun resolveZodType(prop: KSPropertyDeclaration, config: GeneratorConfig): Pair<String, List<String>> {
        val customZodType = prop.annotations.firstOrNull {
            it.shortName.asString() == "ZodType"
        }?.arguments?.firstOrNull()?.value as? String
        if (customZodType != null) return Pair(customZodType, emptyList())
        return resolveZodType(prop.type.resolve(), config)
    }

    private fun resolveZodType(type: KSType, config: GeneratorConfig): Pair<String, List<String>> {
        val isNullable = type.isMarkedNullable
        val imports = mutableListOf<String>()
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
                    imports += classDeclaration.packageName.asString()
                        .replace(".", "/") + "/" + classDeclaration.simpleName.asString()
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
