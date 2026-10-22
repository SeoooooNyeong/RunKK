plugins {
    `kotlin-dsl`
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.ktlint.gradlePlugin)
    compileOnly(libs.roborazzi.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "runkk.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "runkk.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidFeature") {
            id = "runkk.android.feature"
            implementationClass = "AndroidFeatureConventionPlugin"
        }
        register("androidScreenshot") {
            id = "runkk.android.screenshot"
            implementationClass = "AndroidScreenshotConventionPlugin"
        }
        register("jvmLibrary") {
            id = "runkk.jvm.library"
            implementationClass = "JvmLibraryConventionPlugin"
        }
    }
}
