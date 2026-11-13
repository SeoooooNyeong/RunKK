// ui.avatar: 아바타 만들기(셀카·기본 캐릭터), 아바타 꾸미기, 상점 화면과 FeatureEntry(5.1.2).
plugins {
    alias(libs.plugins.runkk.android.feature)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.runkk.android.screenshot)
}

android {
    namespace = "com.a207.runkk.app.ui.avatar"
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    // 아바타 그리기는 공용 Rive 렌더러(W9, S15P31A207-300)를 쓴다.
    implementation(project(":core:avatar"))
    // 셀카 사진의 EXIF 방향을 픽셀에 적용한다(실기기 DV3-3).
    implementation(libs.androidx.exifinterface)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.robolectric)
    testImplementation(testFixtures(project(":core:ui")))
    testImplementation(libs.androidx.espresso.core)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
