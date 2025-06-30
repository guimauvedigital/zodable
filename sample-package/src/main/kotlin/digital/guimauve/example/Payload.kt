package digital.guimauve.example

import digital.guimauve.zodable.Zodable
import kotlinx.serialization.SerialName

@Zodable
sealed class Payload {

    @SerialName("EMPTY")
    data object EmptyPayload : Payload()

    @SerialName("TEXT")
    data class TextPayload(
        val text: String,
    ) : Payload()

}
