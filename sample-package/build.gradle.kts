plugins {
    kotlin("jvm")
    id("digital.guimauve.zodable")
    id("com.google.devtools.ksp")
}

dependencies {
    implementation("dev.kaccelero:core:0.4.5")
    implementation(project(":sample-package-multiplatform"))
}

zodable {
    packageName = "zodable-sample-package"
}
