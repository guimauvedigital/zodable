package digital.guimauve.zodable.extensions

import digital.guimauve.zodable.Optionals
import org.gradle.api.provider.Property

interface ZodableExtension {

    val enableTypescript: Property<Boolean>
    val enablePython: Property<Boolean>
    val inferTypes: Property<Boolean>
    val coerceMapKeys: Property<Boolean>
    val optionals: Property<Optionals>
    val packageName: Property<String>
    val packageVersion: Property<String>
    val valueClassUnwrap: Property<Boolean>

}
