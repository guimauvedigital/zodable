package digital.guimauve.zodable

@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class ZodType(val value: String)
