// core.avatar: 공용 Rive 아바타 렌더러(W9, S15P31A207-300). 아바타 PoC의 남녀 서기·달리기 Rive 몸체, 의상·체형·피부톤 합성,
// 셀카 생성 머리 레이어 변환을 화면 모듈이 Compose 한 줄(RunkkRiveAvatar)로 쓰게 한다. 앱의 다른 모듈은 의존하지 않는다.
plugins {
    alias(libs.plugins.runkk.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.a207.runkk.app.core.avatar"
    buildFeatures {
        compose = true
    }
    testOptions {
        // 레이어 변환·피부톤 시험은 Robolectric 실제 그리기(NATIVE)로 Bitmap·Canvas를 쓴다.
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    // 그을림 상한 값(SkinTone)을 얼굴 원(core.ui)과 함께 쓴다.
    implementation(project(":core:model"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    // 아바타 무대(배경·바닥선·그림자·로딩 표시, design-oo 05-30 4절·D-738)의 Box·Image·원형 진행 표시
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.rive.android)
    implementation(libs.kotlinx.serialization.json)
    // 표시 시간 측정 로그(NFR-061 「AvatarStage shown= ready=」). 앱이 디버그 빌드에서만 DebugTree를 심는다.
    implementation(libs.timber)

    testImplementation(libs.robolectric)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
