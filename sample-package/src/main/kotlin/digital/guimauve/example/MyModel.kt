package digital.guimauve.example

import digital.guimauve.zodable.Zodable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@Zodable
data class MyModel(
    val id: Uuid,
    val name: String,
)
