package dev.zodable.example

import dev.zodable.ZodUnknown
import dev.zodable.Zodable

@Zodable
enum class Country {

    FRANCE,

    US,

    @ZodUnknown
    UNKNOWN,

}
