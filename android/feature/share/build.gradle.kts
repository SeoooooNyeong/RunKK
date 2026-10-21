// ui.share: 공유 카드 이미지 만들기(RunCardRenderer·RunimalCardRenderer)와 공유(CardSharer·ShareTarget 3종)(5.1.2).
// 5.1.2 의존 표상 core.designsystem을 쓰지 않으므로 카드 색·글꼴은 Android Canvas로 직접 그린다.
plugins {
    alias(libs.plugins.runkk.android.feature)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.runkk.android.screenshot)
}

android {
    namespace = "com.a207.runkk.app.ui.share"
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.hilt.android)
    implementation(libs.timber)
    ksp(libs.hilt.compiler)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(testFixtures(project(":core:ui")))
    testImplementation(libs.androidx.test.core)
}
