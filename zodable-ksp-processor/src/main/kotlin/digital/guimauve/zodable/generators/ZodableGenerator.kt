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
                val classFile = resolveClassFile(sourceFolder, packageName, name).also {
                    it.parentFile.mkdirs()
                }.outputStream()

                OutputStreamWriter(classFile, Charsets.UTF_8).use { schemaWriter ->
                    val imports = generateImports(classDeclaration).toMutableSet()
                    val properties = classDeclaration.getAllProperties()
                        .filter { it.hasBackingField }
                        .map { prop ->
                            val name = prop.simpleName.asString()
                            val (type, localImports) = resolveZodType(prop)
                            imports.addAll(localImports)
                            name to type
                        }
                        .toSet()

                    generateImports(imports, schemaWriter)
                    schemaWriter.write("\n")

                    if (classDeclaration.classKind == ClassKind.ENUM_CLASS) {
                        val values = classDeclaration.declarations.filterIsInstance<KSClassDeclaration>()
                            .map { it.simpleName.asString() }
                            .toSet()
                        generateEnumSchema(name, values, schemaWriter)
                    } else {
                        generateClassSchema(name, properties, schemaWriter)
                    }

                    importedPackages.addAll(imports.filter { it.isExternal }.map { it.source })
                }
                generateIndexExport(name, packageName, writer)
            }
        }

        val dependenciesFile = resolveDependenciesFile().outputStream()
        OutputStreamWriter(dependenciesFile, Charsets.UTF_8).use { depWriter ->
            importedPackages.forEach { depWriter.write("$it\n") }
        }
    }

    private fun generateImports(classDeclaration: KSClassDeclaration): Set<Import> =
        classDeclaration.annotations.mapNotNull { annotation ->
            if (annotation.shortName.asString() != "ZodImport") return@mapNotNull null
            val externalName = annotation.arguments.firstOrNull()?.value as? String ?: return@mapNotNull null
            val externalPackageName = annotation.arguments.lastOrNull()?.value as? String ?: return@mapNotNull null
            return@mapNotNull Import(externalName, externalPackageName, true)
        }.toSet()

    private fun resolveZodType(prop: KSPropertyDeclaration): Pair<String, List<Import>> {
        val customZodType = prop.annotations.firstOrNull {
            it.shortName.asString() == "ZodType"
        }?.arguments?.firstOrNull()?.value as? String
        if (customZodType != null) return Pair(customZodType, emptyList())
        return resolveZodType(prop.type.resolve())
    }

    private fun resolveZodType(type: KSType): Pair<String, List<Import>> {
        val isNullable = type.isMarkedNullable
        val imports = mutableListOf<Import>()

        val arguments = type.arguments.map {
            val argument = it.type?.resolve() ?: return@map resolveUnknownType()
            val (argumentType, argumentImports) = resolveZodType(argument)
            imports.addAll(argumentImports)
            argumentType
        }

        val resolvedType = resolvePrimitiveType(type.declaration.qualifiedName?.asString() ?: "kotlin.Any") ?: {
            val classDeclaration = type.declaration as? KSClassDeclaration
            if (classDeclaration != null && classDeclaration.annotations.any { it.shortName.asString() == "Zodable" }) {
                val import = classDeclaration.packageName.asString()
                    .replace(".", "/") + "/" + classDeclaration.simpleName.asString()
                imports += Import(import.split("/").last(), import)
                resolveZodableType(classDeclaration.simpleName.asString())
            } else {
                val unknownType = resolveUnknownType()
                env.logger.warn("Unsupported type ${type.declaration.simpleName}, using $unknownType")
                unknownType
            }
        }()

        return Pair(
            resolvedType
                .let { if (arguments.isNotEmpty()) addGenericArguments(it, arguments) else it }
                .let { if (isNullable) markAsNullable(it) else it },
            imports
        )
    }

    abstract fun resolveSourceFolder(): File
    abstract fun resolveDependenciesFile(): File
    abstract fun resolveIndexFile(sourceFolder: File): File
    abstract fun resolveClassFile(sourceFolder: File, packageName: String, name: String): File
    abstract fun generateImports(imports: Set<Import>, writer: OutputStreamWriter)
    abstract fun generateIndexExport(name: String, packageName: String, writer: OutputStreamWriter)
    abstract fun generateClassSchema(name: String, properties: Set<Pair<String, String>>, writer: OutputStreamWriter)
    abstract fun generateEnumSchema(name: String, values: Set<String>, writer: OutputStreamWriter)
    abstract fun resolvePrimitiveType(kotlinType: String): String?
    abstract fun resolveZodableType(name: String): String
    abstract fun resolveUnknownType(): String
    abstract fun addGenericArguments(type: String, arguments: List<String>): String
    abstract fun markAsNullable(type: String): String

}
