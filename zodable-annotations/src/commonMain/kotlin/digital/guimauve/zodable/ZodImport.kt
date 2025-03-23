package digital.guimauve.zodable

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class ZodImport(
    val name: String,
    val source: String,
)
