plugins {
    kotlin("multiplatform").version("2.1.10").apply(false)
    kotlin("plugin.serialization").version("2.1.10").apply(false)
    id("org.jetbrains.kotlinx.kover").version("0.8.0").apply(false)
    id("com.google.devtools.ksp").version("2.1.10-1.0.30").apply(false)
    id("com.vanniktech.maven.publish").version("0.28.0").apply(false)
}

allprojects {
    group = "digital.guimauve.zodable"
    version = "1.3.0"
    project.ext.set("url", "https://github.com/guimauvedigital/zodable")
    project.ext.set("license.name", "GPL-3.0")
    project.ext.set("license.url", "https://opensource.org/licenses/GPL-3.0")
    project.ext.set("developer.id", "nathanfallet")
    project.ext.set("developer.name", "Nathan Fallet")
    project.ext.set("developer.email", "contact@nathanfallet.me")
    project.ext.set("developer.url", "https://www.nathanfallet.me")
    project.ext.set("scm.url", "https://github.com/guimauvedigital/zodable.git")

    repositories {
        mavenCentral()
    }
}
