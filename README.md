# zodable

Generate zod schemas from Kotlin data classes.

## Install the plugin

Add the following to your `build.gradle.kts`:

```kotlin
plugins {
    id("digital.guimauve.zodable") version "1.0.1"
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

## Configuration options

You can configure a few things in your `build.gradle.kts`:

```kotlin
zodable {
    inferTypes = true // Generate `export type X = z.infer<typeof XSchema>`, default is true
    optionals = digital.guimauve.zodable.Optionals.NULLISH // How to handle optional fields, default is NULLISH
    packageName = "my-package" // npm package name, default is the gradle project name
    packageVersion = "1.0.0" // npm package version, default is the gradle project version
}
```
