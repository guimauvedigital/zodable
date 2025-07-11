package digital.guimauve.example

import dev.kaccelero.models.UUID
import digital.guimauve.zodable.ZodIgnore
import digital.guimauve.zodable.ZodImport
import digital.guimauve.zodable.ZodType
import digital.guimauve.zodable.Zodable
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.SerialName

@ZodImport("IdSchema", "zodable-idschema", isInvariable = true)
@ZodImport("MultiplatformUser", "zodable-sample-package-multiplatform")

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
    val coordinates: Pair<Double, Double>, // Pair of primitive types
    val createdAt: Instant,
    val day: LocalDate,
    val daytime: LocalDateTime,
    val externalUser: MultiplatformUser,
    @ZodType("z.date()", "ts") @ZodType("datetime", "py") val birthDate: String, // Custom mapping
    @ZodType("IdSchema") val otherId: UUID,
    @ZodIgnore val ignored: String, // Ignored property
    @SerialName("custom_name") val customName: String, // Custom serialization name
    val message: Message<String>,
) {

    val notIncluded: Boolean
        get() = true

}
