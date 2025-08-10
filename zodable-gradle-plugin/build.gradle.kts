plugins {
    `kotlin-dsl`
    id("com.gradle.plugin-publish") version "1.2.1"
    id("com.google.devtools.ksp") version "2.1.21-2.0.2"
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

kotlin {
    dependencies {
        implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.1.21")
        implementation("com.google.devtools.ksp:com.google.devtools.ksp.gradle.plugin:2.1.21-2.0.2")
    }
}

group = "digital.guimauve.zodable"
version = "1.6.2"

gradlePlugin {
    website = "https://github.com/guimauvedigital/zodable"
    vcsUrl = "https://github.com/guimauvedigital/zodable.git"

    plugins {
        create("zodable-gradle-plugin") {
            id = "digital.guimauve.zodable"
            implementationClass = "digital.guimauve.zodable.ZodablePlugin"
            displayName = "Zodable"
            description = "Generate zod schemas from Kotlin data classes."
            tags = listOf("zod", "ts", "ksp")
        }
    }
}
