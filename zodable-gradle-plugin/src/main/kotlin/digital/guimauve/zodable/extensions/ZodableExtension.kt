package digital.guimauve.zodable.extensions

import digital.guimauve.zodable.Optionals
import org.gradle.api.provider.Property

interface ZodableExtension {

    val inferTypes: Property<Boolean>
    val coerceMapKeys: Property<Boolean>
    val optionals: Property<Optionals>
    val packageName: Property<String>
    val packageVersion: Property<String>

}
