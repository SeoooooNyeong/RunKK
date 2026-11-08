// ui.notification: 알림함 화면, 이벤트 목록과 상세 웹뷰, ViewModel, FeatureEntry(5.1.2).
plugins {
    alias(libs.plugins.runkk.android.feature)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.runkk.android.screenshot)
}

android {
    namespace = "com.a207.runkk.app.ui.notification"
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.lifecycle.viewmodel.compose)
    // 이벤트 웹 페이지의 open("run"): 자유런 전에 OS 위치 권한 창(홈 자유런 카드와 같다).
    implementation(libs.androidx.activity.compose)
    implementation(libs.timber)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.robolectric)
    testImplementation(testFixtures(project(":core:ui")))
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
