package digital.guimauve.zodable

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Repeatable
annotation class ZodableSchema(
    val name: String,
    val schema: String,
    val filter: String = "*",
)
