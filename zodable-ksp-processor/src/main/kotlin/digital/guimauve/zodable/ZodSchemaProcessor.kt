package digital.guimauve.zodable

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import digital.guimauve.zodable.config.GeneratorConfig
import digital.guimauve.zodable.generators.TypescriptGenerator
import java.nio.file.Paths

class ZodSchemaProcessor(
    private val env: SymbolProcessorEnvironment,
) : SymbolProcessor {

    val packageName = env.options["zodablePackageName"] ?: "zodable"
    val outputPath = env.options["zodableOutputPath"] ?: ""
    val inferTypes = env.options["zodableInferTypes"].equals("true")
    val coerceMapKeys = env.options["zodableCoerceMapKeys"].equals("true")
    val optionals = env.options["zodableOptionals"] ?: ""

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val annotatedClasses = resolver.getSymbolsWithAnnotation(Zodable::class.qualifiedName!!)
            .filterIsInstance<KSClassDeclaration>()

        val outputPath = Paths.get(outputPath).toFile().also { it.mkdirs() }
        val config = GeneratorConfig(packageName, outputPath, inferTypes, coerceMapKeys, optionals)

        TypescriptGenerator(env).generateFiles(annotatedClasses, config)

        return emptyList()
    }

}
