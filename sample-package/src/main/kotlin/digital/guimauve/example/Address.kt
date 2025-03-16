package digital.guimauve.example

import digital.guimauve.zodable.Zodable

@Zodable
data class Address(
    val street: String,
    val city: String,
)
