# zodable

Generate zod schemas from Kotlin data classes.

## Install the plugin

Add the following to your `build.gradle.kts`:

```kotlin
plugins {
    id("digital.guimauve.zodable") version "1.6.1"
    id("com.google.devtools.ksp") version "2.1.10-1.0.30" // Adjust version as needed
}
```

And that's all! The plugin is ready to use.

## Usage

Add the `@Zodable` annotation to your data classes. The plugin will generate a zod schema for each annotated class.

```kotlin
@Zodable
data class User(
    val id: Int,
    val name: String
)
```

Build the schema with `./gradlew build`, like you would with any other gradle project.

The generated schema will look like this:

```typescript
import {z} from "zod"

export const UserSchema = z.object({
    id: z.number().int(),
    name: z.string()
})
export type User = z.infer<typeof UserSchema>
```

Generated schemas can be found in `build/zodable`. It is a ready to use npm package.

Pydantic schema generation is also available, by setting `enablePython` to `true` in the gradle configuration (see
configuration options below). The generated schema will look like this:

```python
from pydantic import BaseModel

class User(BaseModel):
    id: int
    name: str
```

Generated schemas can be found in `build/pydantable`. It is a ready to use pip package.

## Customizing the generated schema with annotations

You can customize the generated schema with annotations:

### `@ZodIgnore`

You can ignore a field with the `@ZodIgnore` annotation:

```kotlin
data class User(
    @ZodIgnore
    val password: String // Will not be included in the generated schema
)
```

### `@ZodImport`

If you are consuming another package/dependency in your schema, you can import it with the `@ZodImport` annotation:

```kotlin
@ZodImport("Pokemon", "my-pokemon-package") // Will generate import and package dependencies

data class User(
    val favoritePokemon: Pokemon // Pokemon is defined in another gradle module/package
)
```

### `@ZodType`

You can specify the zod type for a field with the `@ZodType` annotation:

```kotlin
data class User(
    @ZodType("IdSchema")
    val id: UUID,
    @ZodType("z.date()", "ts")
    @ZodType("datetime", "py")
    val birthDate: String,
)
```

The first argument is the zod type, the second argument is a filter for the target language. If you defined a custom
schema in another package or are only enabling one language, you can omit the filter.

### `@ZodOverrideSchema`

Need the maximum flexibility? You can override the entire schema for a type with the `@ZodOverrideSchema` annotation:

```kotlin
@Zodable
@ZodOverrideSchema(
    content = """
        export const CustomSchema = z.object({
            name: z.string(),
            age: z.number().int(),
            isActive: z.boolean(),
            tags: z.array(z.string()),
        })
        export type Custom = z.infer<typeof CustomSchema>
    """,
    filter = "ts"
)
@ZodOverrideSchema(
    content = """
        from typing import List

        class Custom(BaseModel):
            name: str
            age: int
            is_active: bool
            tags: List[str]
    """,
    filter = "py"
)
interface Custom
```

## Configuration options

You can configure a few things in your `build.gradle.kts`:

```kotlin
zodable {
    inferTypes = true // Generate `export type X = z.infer<typeof XSchema>`, default is true
    optionals = digital.guimauve.zodable.Optionals.NULLISH // How to handle optional fields, default is NULLISH
    packageName = "my-package" // npm package name, default is the gradle project name
    packageVersion = "1.0.0" // npm package version, default is the gradle project version
    enableTypescript = true // Generate typescript schemas, default is true
    enablePython = false // Generate pydantic schemas, default is false
}
```
