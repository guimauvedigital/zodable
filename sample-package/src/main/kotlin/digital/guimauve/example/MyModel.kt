package digital.guimauve.example

import dev.kaccelero.models.IModel
import dev.kaccelero.models.UUID
import digital.guimauve.zodable.Zodable

@Zodable
data class MyModel(
    override val id: UUID,
    val name: String,
) : IModel<UUID, Unit, Unit>
