package digital.guimauve.example

import dev.kaccelero.models.UUID
import digital.guimauve.zodable.ZodImport
import digital.guimauve.zodable.ZodType
import digital.guimauve.zodable.Zodable
import kotlinx.datetime.Instant

@ZodImport("Id", "idschema")
@Zodable
data class User(
    val id: UUID,
    val name: String,
    val email: String?,
    val followers: Int,
    val addresses: List<Address>, // List of another annotated class
    val tags: List<String>, // List of primitive type
    val settings: Map<String, Boolean>, // Map of primitive types
    val eventsByYear: Map<Int, List<String>>, // Map of primitive types, with non-string key
    val contactGroups: Map<String, List<Address>>, // Nested generics
    val createdAt: Instant,
    @ZodType("z.date()") val birthDate: String, // Custom mapping
    @ZodType("IdSchema") val otherId: UUID,
) {

    val notIncluded: Boolean
        get() = true

}
