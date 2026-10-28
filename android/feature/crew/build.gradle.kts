// ui.crew: 크루 찾기·상세·만들기, 내 크루(레이스·크루런·응원보드·크루원), 크루 관리, 크루런 모집·대기실·진행·결과 화면과
// FeatureEntry(5.1.2). v0.11: 공지·투표·일정(대면 모임)·게시판·앨범·공동 목표·신고(A3b). 축하 카드 공유는 ui.share(D-140).
plugins {
    alias(libs.plugins.runkk.android.feature)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.runkk.android.screenshot)
}

android {
    namespace = "com.a207.runkk.app.ui.crew"
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(project(":feature:share"))
    implementation(libs.timber)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.robolectric)
    testImplementation(testFixtures(project(":core:ui")))
    testImplementation(libs.androidx.espresso.core)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
