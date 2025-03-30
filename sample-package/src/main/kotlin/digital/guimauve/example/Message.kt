package digital.guimauve.example

import digital.guimauve.zodable.Zodable

@Zodable
data class Message<T>(
    val content: T,
)
