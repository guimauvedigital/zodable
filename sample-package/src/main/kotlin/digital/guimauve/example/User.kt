package digital.guimauve.example

import dev.kaccelero.models.UUID
import digital.guimauve.zodable.ZodType
import digital.guimauve.zodable.Zodable
import kotlinx.datetime.Instant

@Zodable
data class User(
    val id: UUID,
    val name: String,
    val email: String?,
    val followers: Int,
    val addresses: List<Address>, // List of another annotated class
    val tags: List<String>, // List of primitive type
    val settings: Map<String, Boolean>, // Map of primitive types
    val contactGroups: Map<String, List<Address>>, // Nested generics
    val createdAt: Instant,
    @ZodType("z.date()") val birthDate: String, // Custom mapping
) {

    val notIncluded: Boolean
        get() = true

}
