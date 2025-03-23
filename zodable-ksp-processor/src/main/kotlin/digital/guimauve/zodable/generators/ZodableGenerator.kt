package digital.guimauve.zodable.generators

import com.google.devtools.ksp.symbol.KSClassDeclaration
import digital.guimauve.zodable.config.GeneratorConfig

interface ZodableGenerator {

    fun generateFiles(annotatedClasses: Sequence<KSClassDeclaration>, config: GeneratorConfig)

}
