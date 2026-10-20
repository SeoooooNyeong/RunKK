// 워치 앱(watch.heart·watch.link·watch.ui). 구현은 A4 단계에서 채운다.
plugins {
    alias(libs.plugins.runkk.android.application)
}

android {
    namespace = "com.a207.runkk.watch"

    defaultConfig {
        // Wearable Data Layer는 폰 앱과 같은 applicationId·서명으로 짝을 짓는다.
        applicationId = "com.a207.runkk"
        minSdk = 30
        versionCode = 1
        versionName = "0.1.0"
    }
}

dependencies {
    implementation(project(":wear-contract"))
}
