// 워치 앱(5.1.2 watch.heart·watch.link·watch.ui, 6.1.15). 기록을 만들지 않고 표시·시작 요청·심박 수집만 한다(C-002).
// 의존 방향: watch.ui → watch.link → watch.heart, watch.link → wear.contract. Application·Activity는 watch.ui, Hilt 바인딩은 watch.link.
plugins {
    alias(libs.plugins.runkk.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    // R은 화면 패키지(watch.ui)에 둔다: 5.1.2 의존 표상 watch.ui는 watch.link만 import한다(Konsist 구조 시험).
    namespace = "com.a207.runkk.watch.ui"

    defaultConfig {
        // Wearable Data Layer는 폰 앱과 같은 applicationId·서명으로 짝을 짓는다.
        applicationId = "com.a207.runkk"
        minSdk = 30
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":wear-contract"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.wear.compose.foundation)
    implementation(libs.androidx.wear.compose.material3)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.coroutines.guava)
    implementation(libs.androidx.health.services.client)
    implementation(libs.play.services.wearable)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.kotlinx.coroutines.test)
}
