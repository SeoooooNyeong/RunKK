// ui.record: 기록 목록·달력·상세·카드 공유·성장·리포트·랭킹·코치 화면, ViewModel, CourseMapView, FeatureEntry(5.1.2).
plugins {
    alias(libs.plugins.runkk.android.feature)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.runkk.android.screenshot)
}

android {
    namespace = "com.a207.runkk.app.ui.record"
    buildFeatures {
        buildConfig = true
    }
    defaultConfig {
        // Kakao 지도(C-005) 네이티브 앱 키. 저장소에 넣지 않고(local.properties·Gradle 속성·환경 변수) 없으면 Canvas 경로선만 그린다.
        buildConfigField("String", "KAKAO_NATIVE_APP_KEY", buildConfigString(secretProperty("KAKAO_NATIVE_APP_KEY")))
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(project(":feature:share"))
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kakao.maps)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.robolectric)
    testImplementation(testFixtures(project(":core:ui")))
    testImplementation(libs.androidx.test.core)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
