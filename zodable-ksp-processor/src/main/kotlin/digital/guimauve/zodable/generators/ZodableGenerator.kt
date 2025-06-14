package digital.guimauve.zodable.generators

import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.*
import digital.guimauve.zodable.*
import digital.guimauve.zodable.config.Export
import digital.guimauve.zodable.config.GeneratorConfig
import digital.guimauve.zodable.config.Import
import kotlinx.serialization.SerialName
import java.io.File
import java.io.OutputStreamWriter

abstract class ZodableGenerator(
    protected val env: SymbolProcessorEnvironment,
    protected val config: GeneratorConfig,
) {

    fun generateFiles(annotatedClasses: Sequence<KSClassDeclaration>) {
        val sourceFolder = resolveSourceFolder().also { it.mkdirs() }
        val importedPackages = mutableSetOf<String>()
        val indexFile = resolveIndexFile(sourceFolder).outputStream()

        val exports = annotatedClasses.map { classDeclaration ->
            val name = classDeclaration.simpleName.asString()
            val packageName = classDeclaration.packageName.asString().replace(".", "/")
            val arguments = classDeclaration.typeParameters.map { it.name.asString() }
            val classFile = resolveClassFile(sourceFolder, packageName, name)
            classFile.parentFile.mkdirs()

            OutputStreamWriter(classFile.outputStream(), Charsets.UTF_8).use { schemaWriter ->
                val imports = generateImports(classDeclaration).toMutableSet()

                val overriddenSchema = classDeclaration.annotations.firstNotNullOfOrNull { annotation ->
                    if (annotation.shortName.asString() != ZodOverrideSchema::class.simpleName) return@firstNotNullOfOrNull null
                    val zodOverride = annotation.toZodOverrideSchema()
                    if (!shouldKeepAnnotation("ZodOverrideSchema", zodOverride.filter)) return@firstNotNullOfOrNull null
                    zodOverride.content.trimIndent()
                }

                val generatedBody = overriddenSchema ?: when (classDeclaration.classKind) {
                    ClassKind.ENUM_CLASS -> {
                        val values = classDeclaration.declarations.filterIsInstance<KSClassDeclaration>()
                            .map { it.simpleName.asString() }
                            .toSet()
                        generateEnumSchema(name, arguments, values)
                    }

                    else -> {
                        val properties = classDeclaration.getAllProperties()
                            .filter { it.hasBackingField }
                            .mapNotNull { prop ->
                                val name = prop.annotations.firstNotNullOfOrNull { annotation ->
                                    if (annotation.shortName.asString() != SerialName::class.simpleName) return@firstNotNullOfOrNull null
                                    annotation.toSerialName().value
                                } ?: prop.simpleName.asString()
                                val (type, localImports) = resolveZodType(prop, classDeclaration)
                                    ?: return@mapNotNull null
                                localImports.forEach { import ->
                                    if (imports.none { it.name == import.name }) imports.add(import)
                                }
                                name to type
                            }
                            .toSet()
                        generateClassSchema(name, arguments, properties)
                    }
                }
                val generatedImports = generateImports(sourceFolder, classFile, imports) + "\n"

                schemaWriter.write(generatedImports + "\n")
                schemaWriter.write(generatedBody + "\n")

                importedPackages.addAll(imports.filter { it.isExternal && it.isDependency }.map { it.source })
            }
            Export(name, packageName)
        }

        OutputStreamWriter(indexFile, Charsets.UTF_8).use { indexWriter ->
            indexWriter.write(generateIndexExport(exports) + "\n")
        }

        val dependenciesFile = resolveDependenciesFile().outputStream()
        OutputStreamWriter(dependenciesFile, Charsets.UTF_8).use { depWriter ->
            importedPackages.forEach { depWriter.write("$it\n") }
        }
    }

    private fun generateImports(classDeclaration: KSClassDeclaration): Set<Import> =
        resolveDefaultImports(classDeclaration) + classDeclaration.annotations.mapNotNull { annotation ->
            if (annotation.shortName.asString() != ZodImport::class.simpleName) return@mapNotNull null
            val zodImport = annotation.toZodImport()
            if (!shouldKeepAnnotation("ZodImport", zodImport.filter)) return@mapNotNull null
            Import(zodImport.name, zodImport.source, true, zodImport.isInvariable)
        }.toSet()

    private fun resolveZodType(
        prop: KSPropertyDeclaration,
        classDeclaration: KSClassDeclaration,
    ): Pair<String, List<Import>>? {
        prop.annotations.forEach { annotation ->
            if (annotation.shortName.asString() != ZodIgnore::class.simpleName) return@forEach
            val zodIgnore = annotation.toZodIgnore()
            if (!shouldKeepAnnotation("ZodIgnore", zodIgnore.filter)) return@forEach
            return@resolveZodType null
        }
        val customZodType = prop.annotations.firstNotNullOfOrNull { annotation ->
            if (annotation.shortName.asString() != ZodType::class.simpleName) return@firstNotNullOfOrNull null
            val zodType = annotation.toZodType()
            if (!shouldKeepAnnotation("ZodType", zodType.filter)) return@firstNotNullOfOrNull null
            zodType.value
        }
        if (customZodType != null) return Pair(customZodType, emptyList())
        return resolveZodType(prop.type.resolve(), classDeclaration)
    }

    private fun resolveZodType(type: KSType, classDeclaration: KSClassDeclaration): Pair<String, List<Import>> {
        val isNullable = type.isMarkedNullable
        val imports = mutableListOf<Import>()

        val (arguments, argumentImports) = type.arguments.map {
            val argument = it.type?.resolve() ?: return@map resolveUnknownType()
            resolveZodType(argument, classDeclaration)
        }.unzip().let { it.first to it.second.flatten() }

        val (resolvedType, resolvedImports) = resolvePrimitiveType(
            type.declaration.qualifiedName?.asString() ?: "kotlin.Any"
        ) ?: {
            val typeDeclaration = type.declaration as? KSClassDeclaration
            if (typeDeclaration != null && typeDeclaration.annotations.any { it.shortName.asString() == Zodable::class.simpleName }) {
                val import = typeDeclaration.packageName.asString()
                    .replace(".", "/") + "/" + typeDeclaration.simpleName.asString()
                imports += Import(import.split("/").last(), import)
                resolveZodableType(typeDeclaration.simpleName.asString(), typeDeclaration.typeParameters.isNotEmpty())
            } else if (classDeclaration.typeParameters.any { it.name.asString() == type.declaration.simpleName.asString() }) {
                resolveGenericArgument(type.declaration.simpleName.asString())
            } else {
                val unknownType = resolveUnknownType()
                env.logger.warn("Unsupported type ${type.declaration.simpleName.asString()}, using ${unknownType.first}")
                unknownType
            }
        }()

        val allImports = imports + argumentImports + resolvedImports
        return (resolvedType to allImports)
            .let {
                if (arguments.isNotEmpty()) {
                    val (newType, newImports) = addGenericArguments(it.first, arguments)
                    newType to (it.second + newImports)
                } else it
            }
            .let {
                if (isNullable) {
                    val (newType, newImports) = markAsNullable(it.first)
                    newType to (it.second + newImports)
                } else it
            }
    }

    private fun KSAnnotation.toZodOverrideSchema(): ZodOverrideSchema {
        val args = arguments.associateBy { it.name?.asString() }
        return ZodOverrideSchema(
            content = args["content"]?.value as? String ?: error("Missing 'content'"),
            filter = args["filter"]?.value as? String ?: "*",
        )
    }

    private fun KSAnnotation.toZodImport(): ZodImport {
        val args = arguments.associateBy { it.name?.asString() }
        return ZodImport(
            name = args["name"]?.value as? String ?: error("Missing 'name'"),
            source = args["source"]?.value as? String ?: error("Missing 'source'"),
            filter = args["filter"]?.value as? String ?: "*",
            isInvariable = args["isInvariable"]?.value as? Boolean == true
        )
    }

    private fun KSAnnotation.toZodIgnore(): ZodIgnore {
        val args = arguments.associateBy { it.name?.asString() }
        return ZodIgnore(
            filter = args["filter"]?.value as? String ?: "*",
        )
    }

    private fun KSAnnotation.toZodType(): ZodType {
        val args = arguments.associateBy { it.name?.asString() }
        return ZodType(
            value = args["value"]?.value as? String ?: error("Missing 'type'"),
            filter = args["filter"]?.value as? String ?: "*",
        )
    }

    private fun KSAnnotation.toSerialName(): SerialName {
        val args = arguments.associateBy { it.name?.asString() }
        return SerialName(
            value = args["value"]?.value as? String ?: "*",
        )
    }

    abstract fun shouldKeepAnnotation(annotation: String, filter: String): Boolean
    abstract fun resolveSourceFolder(): File
    abstract fun resolveDependenciesFile(): File
    abstract fun resolveIndexFile(sourceFolder: File): File
    abstract fun resolveClassFile(sourceFolder: File, packageName: String, name: String): File
    abstract fun resolveDefaultImports(classDeclaration: KSClassDeclaration): Set<Import>
    abstract fun generateImports(sourceFolder: File, currentFile: File, imports: Set<Import>): String
    abstract fun generateIndexExport(exports: Sequence<Export>): String
    abstract fun generateClassSchema(
        name: String,
        arguments: List<String>,
        properties: Set<Pair<String, String>>,
    ): String

    abstract fun generateEnumSchema(name: String, arguments: List<String>, values: Set<String>): String
    abstract fun resolvePrimitiveType(kotlinType: String): Pair<String, List<Import>>?
    abstract fun resolveZodableType(name: String, isGeneric: Boolean): Pair<String, List<Import>>
    abstract fun resolveGenericArgument(name: String): Pair<String, List<Import>>
    abstract fun resolveUnknownType(): Pair<String, List<Import>>
    abstract fun addGenericArguments(type: String, arguments: List<String>): Pair<String, List<Import>>
    abstract fun markAsNullable(type: String): Pair<String, List<Import>>

}
