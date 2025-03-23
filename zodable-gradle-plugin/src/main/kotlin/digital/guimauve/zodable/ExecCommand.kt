package digital.guimauve.zodable

data class ExecCommand(
    val commandLine: List<String>,
    val standardInput: String? = null,
)
