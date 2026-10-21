// ui.me: 로그인·단계별 가입·마이(프로필·설정·동의·탈퇴) 화면, ViewModel, FeatureEntry, SocialLoginClient(5.1.2).
plugins {
    alias(libs.plugins.runkk.android.feature)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.runkk.android.screenshot)
}

// 제공자 앱 키(C-007)는 저장소에 넣지 않는다: local.properties·Gradle 속성·환경 변수 KAKAO_NATIVE_APP_KEY, GOOGLE_WEB_CLIENT_ID.
// 비어 있으면 그 로그인은 "준비 중"을 돌려주고 빌드·시험은 키 없이 통과한다.
val kakaoNativeAppKey = secretProperty("KAKAO_NATIVE_APP_KEY")
val googleWebClientId = secretProperty("GOOGLE_WEB_CLIENT_ID")

android {
    namespace = "com.a207.runkk.app.ui.me"
    buildFeatures {
        buildConfig = true
    }
    defaultConfig {
        buildConfigField("String", "KAKAO_NATIVE_APP_KEY", buildConfigString(kakaoNativeAppKey))
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", buildConfigString(googleWebClientId))
        // Kakao SDK 리다이렉트 스킴(kakao{앱 키}). SDK를 붙일 때 AuthCodeHandlerActivity의 intent-filter가 ${kakaoScheme}을 쓴다.
        manifestPlaceholders["kakaoScheme"] = "kakao$kakaoNativeAppKey"
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.google.googleid)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.robolectric)
    testImplementation(testFixtures(project(":core:ui")))
    testImplementation(libs.androidx.test.core)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
