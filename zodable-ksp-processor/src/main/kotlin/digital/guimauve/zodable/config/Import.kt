package digital.guimauve.zodable.config

data class Import(
    val name: String,
    val source: String,
    val isExternal: Boolean = false,
)
