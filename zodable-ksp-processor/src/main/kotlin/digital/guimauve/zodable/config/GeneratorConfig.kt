package digital.guimauve.zodable.config

import java.io.File

data class GeneratorConfig(
    val outputPath: File,
    val inferTypes: Boolean,
    val coerceMapKeys: Boolean,
    val optionals: String,
)
