pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "zodable"
includeBuild("zodable-gradle-plugin")
include(":zodable-annotations")
include(":zodable-ksp-processor")

include(":sample-package")
include(":sample-package-multiplatform")
