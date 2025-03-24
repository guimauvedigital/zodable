package digital.guimauve.example

import dev.kaccelero.models.UUID
import digital.guimauve.zodable.Zodable

@Zodable
data class MultiplatformUser(
    val id: UUID,
    val type: MultiplatformType,
)
