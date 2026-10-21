plugins {
    alias(libs.plugins.runkk.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// FCM(firebase-messaging)은 google-services.json이 있어야 초기화된다. 파일은 저장소에 넣지 않으므로(비밀 설정) 있을 때만
// 플러그인을 적용하고, 없으면 푸시 등록만 건너뛴 채 빌드·실행이 된다(PushTokenSync는 토큰이 없으면 아무것도 하지 않는다).
if (file("google-services.json").exists()) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
}

android {
    // 패키지 루트는 설계서 0.1의 com.a207.runkk.app. applicationId는 스토어 식별자라 그대로 둔다.
    namespace = "com.a207.runkk.app"

    defaultConfig {
        applicationId = "com.a207.runkk"
        versionCode = 1
        versionName = "0.1.0"
    }

    // CI(main)에서만 서명한다: 키스토어는 RUNKK_KEYSTORE_* 환경 변수로 전달 (.gitlab-ci.yml android:release).
    // 변수가 없으면 release는 서명 없이 빌드되고, 저장소에는 .jks를 넣지 않는다.
    val keystorePath = System.getenv("RUNKK_KEYSTORE_PATH")
    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("RUNKK_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RUNKK_KEY_ALIAS")
                keyPassword = System.getenv("RUNKK_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // R8 축소·난독화(리뷰 N-2). 반사로 읽는 곳의 보존 규칙은 proguard-rules.pro, 확인은 assembleProdRelease.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystorePath != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    // 서버 주소(API_BASE_URL·LIVE_SOCKET_URL)는 :data(api.core)의 플레이버 BuildConfig 한 곳에서만 관리한다(리뷰 N-4).
    // app 코드는 서버 주소를 읽지 않는다(5.1.2: 서버 주소는 api.core만 읽는다).
}

// E2E 시험(e2e/README.md) 전용: local 플레이버 + debug 빌드에만 시험용 카카오 토큰 창구(src/localDebug)와 그 토큰 값을 넣는다.
// 빌드 속성 RUNKK_E2E_KAKAO_TOKEN(local.properties·-P·환경 변수)이 비면 창구는 꺼져 Kakao SDK를 그대로 쓰고,
// 다른 변형(localRelease, develop*, prod*)에는 코드도 값도 들어가지 않는다(E2eLoginPathTest).
val e2eKakaoToken = secretProperty("RUNKK_E2E_KAKAO_TOKEN")
androidComponents {
    onVariants(selector().withName("localDebug")) { variant ->
        variant.buildConfigFields?.put(
            "E2E_KAKAO_TOKEN",
            com.android.build.api.variant.BuildConfigField("String", buildConfigString(e2eKakaoToken), "E2E 시험용 카카오 토큰")
        )
    }
}

dependencies {
    // 조립 루트: Hilt가 모든 모듈의 @InstallIn 바인딩과 FeatureEntry @IntoSet을 집계하도록 전부 의존한다.
    // 코드 import는 5.1.2 표(app → domain.sync·account·settings·notification, core.navigation·designsystem)만 쓴다.
    implementation(project(":core:model"))
    implementation(project(":core:ui"))
    implementation(project(":domain"))
    implementation(project(":data"))
    implementation(project(":feature:home"))
    implementation(project(":feature:run"))
    implementation(project(":feature:record"))
    implementation(project(":feature:battle"))
    implementation(project(":feature:crew"))
    implementation(project(":feature:runimal"))
    implementation(project(":feature:avatar"))
    implementation(project(":feature:me"))
    implementation(project(":feature:notification"))
    implementation(project(":feature:share"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.timber)
    ksp(libs.hilt.compiler)

    // 구조 시험(14장): 5.1.2 의존 표·domain 순수성 규칙을 Konsist로 확인한다.
    testImplementation(libs.konsist)
    // 시작 경로·하단 탭 시험(Robolectric Compose)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
