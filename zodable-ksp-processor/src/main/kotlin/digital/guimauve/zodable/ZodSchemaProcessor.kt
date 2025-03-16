package digital.guimauve.zodable

import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import java.io.OutputStreamWriter

class ZodSchemaProcessor(
    private val env: SymbolProcessorEnvironment,
) : SymbolProcessor {

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val annotatedClasses = resolver.getSymbolsWithAnnotation(Zodable::class.qualifiedName!!)
            .filterIsInstance<KSClassDeclaration>()

        if (annotatedClasses.iterator().hasNext()) {
            val file = env.codeGenerator.createNewFile(
                Dependencies(false, *annotatedClasses.map { it.containingFile!! }.toList().toTypedArray()),
                "zodable", "schemas", "ts"
            )

            OutputStreamWriter(file, Charsets.UTF_8).use { writer ->
                writer.write("import {z} from \"zod\";\n\n")
                for (classDeclaration in annotatedClasses) {
                    val className = classDeclaration.simpleName.asString()
                    val properties = classDeclaration.getAllProperties().joinToString(",\n    ") { prop ->
                        val name = prop.simpleName.asString()
                        val type = resolveZodType(prop)
                        "$name: $type"
                    }
                    writer.write("export const ${className}Schema = z.object({\n    $properties\n})\n")
                    writer.write("export type $className = z.infer<typeof ${className}Schema>\n\n")
                }
            }
        }
        return emptyList()
    }

    private fun resolveZodType(prop: KSPropertyDeclaration): String {
        val customZodType = prop.annotations.firstOrNull {
            it.shortName.asString() == "ZodType"
        }?.arguments?.firstOrNull()?.value as? String
        if (customZodType != null) return customZodType
        return resolveZodType(prop.type.resolve())
    }

    private fun resolveZodType(type: KSType): String {
        val isNullable = type.isMarkedNullable
        val zodType = when (type.declaration.qualifiedName?.asString()) {
            "kotlin.String" -> "z.string()"
            "kotlin.Int" -> "z.number().int()"
            "kotlin.Long", "kotlin.Double", "kotlin.Float" -> "z.number()"
            "kotlin.Boolean" -> "z.boolean()"
            "dev.kaccelero.models.UUID" -> "z.string().uuid()"
            "kotlin.collections.List" -> {
                val typeArg = type.arguments.firstOrNull()?.type?.resolve()
                if (typeArg != null) "z.array(${resolveZodType(typeArg)})" else "z.array(z.unknown())"
            }

            "kotlin.collections.Map" -> {
                val keyType = type.arguments.getOrNull(0)?.type?.resolve()
                val valueType = type.arguments.getOrNull(1)?.type?.resolve()
                if (keyType != null && valueType != null) {
                    "z.record(${resolveZodType(keyType)}, ${resolveZodType(valueType)})"
                } else "z.record(z.string(), z.unknown())"
            }

            else -> {
                val classDeclaration = type.declaration as? KSClassDeclaration
                if (classDeclaration != null && classDeclaration.annotations.any { it.shortName.asString() == "Zodable" }) {
                    "${classDeclaration.simpleName.asString()}Schema"
                } else "z.unknown()"
            }
        }
        return if (isNullable) "$zodType.nullish()" else zodType
    }

}
