package digital.guimauve.example

import dev.kaccelero.models.UUID
import digital.guimauve.zodable.Zodable

@Zodable
data class User(
    val id: UUID,
)
