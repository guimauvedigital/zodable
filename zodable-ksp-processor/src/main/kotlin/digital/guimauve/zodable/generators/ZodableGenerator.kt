package digital.guimauve.zodable.generators

import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import digital.guimauve.zodable.config.GeneratorConfig
import digital.guimauve.zodable.config.Import
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
        OutputStreamWriter(indexFile, Charsets.UTF_8).use { writer ->
            for (classDeclaration in annotatedClasses) {
                val name = classDeclaration.simpleName.asString()
                val packageName = classDeclaration.packageName.asString().replace(".", "/")
                val arguments = classDeclaration.typeParameters.map { it.name.asString() }
                val classFile = resolveClassFile(sourceFolder, packageName, name)
                classFile.parentFile.mkdirs()

                OutputStreamWriter(classFile.outputStream(), Charsets.UTF_8).use { schemaWriter ->
                    val imports = generateImports(classDeclaration).toMutableSet()

                    val generatedBody = when (classDeclaration.classKind) {
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
                                    val name = prop.simpleName.asString()
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
                writer.write(generateIndexExport(name, packageName) + "\n")
            }
        }

        val dependenciesFile = resolveDependenciesFile().outputStream()
        OutputStreamWriter(dependenciesFile, Charsets.UTF_8).use { depWriter ->
            importedPackages.forEach { depWriter.write("$it\n") }
        }
    }

    private fun generateImports(classDeclaration: KSClassDeclaration): Set<Import> =
        resolveDefaultImports(classDeclaration) + classDeclaration.annotations.mapNotNull { annotation ->
            if (annotation.shortName.asString() != "ZodImport") return@mapNotNull null
            val externalName = annotation.arguments.getOrNull(0)?.value as? String ?: return@mapNotNull null
            val externalPackageName =
                annotation.arguments.getOrNull(1)?.value as? String ?: return@mapNotNull null
            val filter = annotation.arguments.getOrNull(2)?.value as? String
            if (filter != null && !shouldKeepAnnotation("ZodImport", filter)) return@mapNotNull null
            Import(externalName, externalPackageName, true)
        }.toSet()

    private fun resolveZodType(
        prop: KSPropertyDeclaration,
        classDeclaration: KSClassDeclaration,
    ): Pair<String, List<Import>>? {
        prop.annotations.forEach { annotation ->
            if (annotation.shortName.asString() != "ZodIgnore") return@forEach
            val filter = annotation.arguments.getOrNull(0)?.value as? String
            if (filter != null && !shouldKeepAnnotation("ZodIgnore", filter)) return@forEach
            return@resolveZodType null
        }
        val customZodType = prop.annotations.firstNotNullOfOrNull { annotation ->
            if (annotation.shortName.asString() != "ZodType") return@firstNotNullOfOrNull null
            val type = annotation.arguments.getOrNull(0)?.value as? String ?: return@firstNotNullOfOrNull null
            val filter = annotation.arguments.getOrNull(1)?.value as? String
            if (filter != null && !shouldKeepAnnotation("ZodType", filter)) return@firstNotNullOfOrNull null
            type
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
            if (typeDeclaration != null && typeDeclaration.annotations.any { it.shortName.asString() == "Zodable" }) {
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

    abstract fun shouldKeepAnnotation(annotation: String, filter: String): Boolean
    abstract fun resolveSourceFolder(): File
    abstract fun resolveDependenciesFile(): File
    abstract fun resolveIndexFile(sourceFolder: File): File
    abstract fun resolveClassFile(sourceFolder: File, packageName: String, name: String): File
    abstract fun resolveDefaultImports(classDeclaration: KSClassDeclaration): Set<Import>
    abstract fun generateImports(sourceFolder: File, currentFile: File, imports: Set<Import>): String
    abstract fun generateIndexExport(name: String, packageName: String): String
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
