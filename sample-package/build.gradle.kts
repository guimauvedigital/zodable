plugins {
    kotlin("jvm")
    id("digital.guimauve.zodable")
    id("com.google.devtools.ksp")
}

dependencies {
    api(project(":sample-package-multiplatform"))
}

zodable {
    packageName = "zodable-sample-package"
    enablePython = true // Default is false
    valueClassUnwrap = false
}
