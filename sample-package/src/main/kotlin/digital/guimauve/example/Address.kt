package digital.guimauve.example

import digital.guimauve.zodable.Zodable

@Zodable
data class Address(
    val street: Street,
    val city: String,
    val country: Country,
)
