package digital.guimauve.example

import digital.guimauve.zodable.Zodable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@Zodable
data class MultiplatformUser(
    val id: Uuid,
    val type: MultiplatformType,
)
