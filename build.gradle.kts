// Top-level build file. Individual module build files apply the plugins they need.
plugins {
    id("com.android.application") version "8.6.1" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    // Kotlin 2.0+ ships the Compose compiler as its own Gradle plugin rather
    // than a `composeOptions { kotlinCompilerExtensionVersion }` version
    // string — that old mechanism is gone, this replaces it.
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("com.google.devtools.ksp") version "2.0.21-1.0.28" apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
