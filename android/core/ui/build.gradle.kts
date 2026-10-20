// core.designsystem(공용 Compose 구성 요소)과 core.navigation(경로 이름·기능 등록 계약)(5.1.2).
plugins {
    alias(libs.plugins.runkk.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.a207.runkk.app.core.ui"
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.material3)
    api(libs.androidx.navigation3.runtime)
    implementation(libs.kotlinx.serialization.json)
}
