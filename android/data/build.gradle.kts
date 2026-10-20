// api.*·data.*: 도메인 포트 구현(5.1.2). 구현은 A2 단계에서 채운다.
plugins {
    alias(libs.plugins.runkk.android.library)
}

android {
    namespace = "com.a207.runkk.app.data"
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":core:model"))
    implementation(project(":wear-contract"))
}
