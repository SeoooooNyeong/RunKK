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
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.rive.android)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.robolectric)
}
