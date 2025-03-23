package digital.guimauve.zodable.generators

import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import digital.guimauve.zodable.config.GeneratorConfig
import digital.guimauve.zodable.config.Import
import java.io.File
import java.io.OutputStreamWriter

abstract class ZodableGenerator {

    fun generateFiles(annotatedClasses: Sequence<KSClassDeclaration>, config: GeneratorConfig) {
        val sourceFolder = resolveSourceFolder(config).also { it.mkdirs() }
        val importedPackages = mutableSetOf<String>()
        val indexFile = resolveIndexFile(sourceFolder, config).outputStream()
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
                            val (type, localImports) = resolveZodType(prop, config)
                            imports.addAll(localImports)
                            name to type
                        }
                        .toSet()

                    generateImports(imports, schemaWriter, config)
                    schemaWriter.write("\n")

                    if (classDeclaration.classKind == ClassKind.ENUM_CLASS) {
                        val values = classDeclaration.declarations.filterIsInstance<KSClassDeclaration>()
                            .map { it.simpleName.asString() }
                            .toSet()
                        generateEnumSchema(name, values, schemaWriter, config)
                    } else {
                        generateClassSchema(name, properties, schemaWriter, config)
                    }

                    importedPackages.addAll(imports.filter { it.isExternal }.map { it.source })
                }
                generateIndexExport(name, packageName, writer, config)
            }
        }

        val dependenciesFile = resolveDependenciesFile(config).outputStream()
        OutputStreamWriter(dependenciesFile, Charsets.UTF_8).use { depWriter ->
            importedPackages.forEach { depWriter.write("$it\n") }
        }
    }

    fun generateImports(
        classDeclaration: KSClassDeclaration,
    ): Set<Import> = classDeclaration.annotations.mapNotNull { annotation ->
        if (annotation.shortName.asString() != "ZodImport") return@mapNotNull null
        val externalName = annotation.arguments.firstOrNull()?.value as? String ?: return@mapNotNull null
        val externalPackageName = annotation.arguments.lastOrNull()?.value as? String ?: return@mapNotNull null
        return@mapNotNull Import(externalName, externalPackageName, true)
    }.toSet()

    abstract fun resolveSourceFolder(
        config: GeneratorConfig,
    ): File

    abstract fun resolveDependenciesFile(
        config: GeneratorConfig,
    ): File

    abstract fun resolveIndexFile(
        sourceFolder: File,
        config: GeneratorConfig,
    ): File

    abstract fun resolveClassFile(
        sourceFolder: File,
        packageName: String,
        name: String,
    ): File

    abstract fun generateImports(
        imports: Set<Import>,
        writer: OutputStreamWriter,
        config: GeneratorConfig,
    )

    abstract fun generateIndexExport(
        name: String,
        packageName: String,
        writer: OutputStreamWriter,
        config: GeneratorConfig,
    )

    abstract fun generateClassSchema(
        name: String,
        properties: Set<Pair<String, String>>,
        writer: OutputStreamWriter,
        config: GeneratorConfig,
    )

    abstract fun generateEnumSchema(
        name: String,
        values: Set<String>,
        writer: OutputStreamWriter,
        config: GeneratorConfig,
    )

    abstract fun resolveZodType(
        prop: KSPropertyDeclaration,
        config: GeneratorConfig,
    ): Pair<String, List<Import>>

}
