// core.designsystem(공용 Compose 구성 요소)과 core.navigation(경로 이름·기능 등록 계약)(5.1.2).
plugins {
    alias(libs.plugins.runkk.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.runkk.android.screenshot)
}

android {
    // 리소스(R)가 core.designsystem 패키지에 생기도록 네임스페이스를 맞춘다(Konsist: 5.1.2 표 밖 패키지 import 0).
    namespace = "com.a207.runkk.app.core.designsystem"
    buildFeatures {
        compose = true
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
    // 화면 모듈 시험이 같이 쓰는 스크린샷 도우미(RunkkSnapshot): testImplementation(testFixtures(project(":core:ui")))
    testFixtures {
        enable = true
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.material3)
    api(libs.androidx.navigation3.runtime)
    api(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.kotlinx.serialization.json)
    // 측정 화면 지도(C-005): 기록 상세와 같은 Kakao 지도 SDK v2
    implementation(libs.kakao.maps)
    implementation(libs.androidx.lifecycle.runtime.compose)
    // 배경 음악·로그인 배경 영상 반복 재생(다음 반복을 미리 읽어 이음매 없이 잇는다, 사용자 요청 2026-10-31)
    implementation(libs.androidx.media3.exoplayer)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.robolectric)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testFixturesImplementation(platform(libs.androidx.compose.bom))
    testFixturesImplementation(libs.androidx.compose.ui.test.junit4)
    testFixturesImplementation(libs.roborazzi)
    testFixturesImplementation(libs.roborazzi.compose)
}
