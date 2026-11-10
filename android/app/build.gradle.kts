plugins {
    alias(libs.plugins.runkk.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// FCM(firebase-messaging)은 google-services.json이 있어야 초기화된다. 파일은 저장소에 넣지 않으므로(비밀 설정) 어느 위치에든
// 있을 때만 플러그인을 적용하고, 없으면 푸시 등록만 건너뛴 채 빌드·실행이 된다(FcmPushTokenSource는 FirebaseApp이 없으면 null,
// PushTokenSync는 토큰이 없으면 아무것도 하지 않는다).
// 플러그인은 변형마다 json을 찾아(src/<변형>, src/<플레이버>, src/<빌드타입>, app/) 그 안에 변형의 applicationId와 같은
// client.package_name이 없으면 빌드를 실패시킨다. 그래서 등록되지 않은 패키지의 변형(예: .local·.dev)은 처리 태스크를 끄고 경고한다.
val googleServicesCandidates = listOf("google-services.json") +
    fileTree("src") { include("*/google-services.json", "*/*/google-services.json") }.files
        .map { it.relativeTo(projectDir).invariantSeparatorsPath }
if (googleServicesCandidates.any { file(it).exists() }) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
}

fun googleServicesPackages(json: File): Set<String> =
    @Suppress("UNCHECKED_CAST")
    ((groovy.json.JsonSlurper().parse(json) as Map<String, Any?>)["client"] as? List<Map<String, Any?>>)
        .orEmpty()
        .mapNotNull {
            (
                (it["client_info"] as? Map<*, *>)?.get(
                    "android_client_info"
                ) as? Map<*, *>
                )?.get("package_name") as? String
        }
        .toSet()

val kakaoNativeAppKey = secretProperty("KAKAO_NATIVE_APP_KEY")

android {
    // 패키지 루트는 설계서 0.1의 com.a207.runkk.app. applicationId는 스토어 식별자라 그대로 둔다.
    namespace = "com.a207.runkk.app"

    defaultConfig {
        applicationId = "com.a207.runkk"
        // 카카오 SDK 초기화(D-275)와 카카오톡 실행 링크 intent-filter(D-272)가 쓰는 앱 키. 저장소에 넣지 않는다(local.properties·
        // Gradle 속성·환경 변수 KAKAO_NATIVE_APP_KEY, ui.me 로그인과 같은 값). 비면 초기화를 건너뛰고 스킴은 "kakao"만 남는다.
        buildConfigField("String", "KAKAO_NATIVE_APP_KEY", buildConfigString(kakaoNativeAppKey))
        // 키가 비면 아무 앱도 쓰지 않는 스킴을 둬 kakao://kakaolink 링크를 받지 않는다(보안 리뷰).
        manifestPlaceholders["kakaoScheme"] =
            if (kakaoNativeAppKey.isBlank()) "runkk-kakao-disabled" else "kakao$kakaoNativeAppKey"
        // 폰은 IID×10, 워치는 IID×10+1로 Play 버전 코드 충돌을 막는다.
        versionCode = providers.gradleProperty("runkk.versionCode").orElse("10").get().toInt()
        versionName = "0.11.5"
    }

    // 서명은 runkk.android.application 공통 플러그인이 RUNKK_KEYSTORE_* 환경 변수로 정한다(.gitlab-ci.yml).
    buildTypes {
        release {
            // R8 축소·난독화(리뷰 N-2). 반사로 읽는 곳의 보존 규칙은 proguard-rules.pro, 확인은 assembleProdRelease.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    // APK 크기: 43MB 중 37MB가 Kakao 지도 SDK(libK3fAndroid.so arm64 18MB·armeabi-v7a 16MB, 글꼴 3.5MB)다.
    // 직접 주고받는 APK는 ABI마다 따로 만들고(app-<플레이버>-<ABI>-<빌드타입>.apk, 요즘 폰은 arm64 하나면 되고 x86_64는 에뮬레이터용)
    // .so를 압축해 담는다(arm64 18MB -> 6MB, 대신 설치 때 풀려 설치 용량이 그만큼 는다).
    // Play 배포 AAB는 -Prunkk.playBundle=true로 둘 다 끈다(CI android:release): AGP는 ABI 분할을 켠 채 번들을 만들지 못하고
    // (issuetracker 402800800), ABI 분할은 Play가 하며, .so는 압축하지 않는 기본값이 설치 용량에 낫다.
    val playBundle = providers.gradleProperty("runkk.playBundle").orNull == "true"
    splits {
        abi {
            isEnable = !playBundle
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = false
        }
    }
    packaging {
        jniLibs {
            useLegacyPackaging = !playBundle
        }
    }

    // 라이브러리(AndroidX·Play 서비스 등)가 가져오는 97개 언어 리소스 중 한국어·영어만 남긴다. 앱 문구는 기본 values(한국어)에 있다.
    androidResources {
        localeFilters += listOf("ko", "en")
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
    onVariants { variant ->
        if (!plugins.hasPlugin(libs.plugins.google.services.get().pluginId)) return@onVariants
        val flavors = variant.productFlavors.map { it.second }
        val buildType = variant.buildType.orEmpty()
        // 플러그인의 탐색 위치와 같은 곳(변형 이름, 플레이버, 빌드 타입, 앱 루트)을 우선순위 순으로 본다.
        val dirs =
            listOf(variant.name) + flavors + listOfNotNull(buildType.ifEmpty { null }) +
                flavors.map { it + buildType.replaceFirstChar(Char::uppercase) } +
                ""
        val json = dirs.map {
            if (it.isEmpty()) file("google-services.json") else file("src/$it/google-services.json")
        }.firstOrNull { it.exists() }
        val appId = variant.applicationId.get()
        if (json == null || appId !in googleServicesPackages(json)) {
            val reason = if (json ==
                null
            ) {
                "google-services.json 없음"
            } else {
                "${json.relativeTo(projectDir)}에 패키지 $appId 미등록"
            }
            logger.warn("경고: ${variant.name} 변형은 google-services 처리를 건너뛴다($reason). FCM이 초기화되지 않는다.")
            val taskName = "process${variant.name.replaceFirstChar(Char::uppercase)}GoogleServices"
            tasks.configureEach { if (name == taskName) enabled = false }
        }
    }
    // 1차 배포 잠금(D-251, BetaLock.kt·ReleaseScope): prod·develop 플레이버는 참(팀 시험 빌드도 출시 모습), local은 거짓이다.
    // Gradle 속성 -Prunkk.lockUnreleased=true|false가 있으면 플레이버 기본값보다 앞선다(2차 배포 기능 개발용).
    val lockUnreleasedOverride = providers.gradleProperty("runkk.lockUnreleased").orNull?.toBooleanStrictOrNull()
    onVariants { variant ->
        val local = variant.productFlavors.any { it.second == "local" }
        val lock = lockUnreleasedOverride ?: !local
        variant.buildConfigFields?.put(
            "LOCK_UNRELEASED",
            com.android.build.api.variant.BuildConfigField("boolean", lock.toString(), "1차 배포에서 열지 않는 기능을 막는가")
        )
    }
    // 카카오 네이티브 앱 키는 패키지마다 따로 있다(콘솔: 기본 = 운영, _DEVELOP = .dev, _LOCAL = .local). 플레이버 키가 비면 기본 키를 쓴다.
    // 지도(KakaoMapSdk)·공유·초대 링크 스킴이 이 값을 쓴다.
    val kakaoKeyByFlavor = mapOf(
        "local" to secretProperty("KAKAO_NATIVE_APP_KEY_LOCAL"),
        "develop" to secretProperty("KAKAO_NATIVE_APP_KEY_DEVELOP")
    )
    onVariants { variant ->
        val flavor = variant.productFlavors.firstOrNull()?.second
        val kakaoKey = kakaoKeyByFlavor[flavor]?.takeIf { it.isNotBlank() } ?: kakaoNativeAppKey
        variant.buildConfigFields?.put(
            "KAKAO_NATIVE_APP_KEY",
            com.android.build.api.variant.BuildConfigField("String", buildConfigString(kakaoKey), "카카오 네이티브 앱 키(플레이버별)")
        )
        variant.manifestPlaceholders.put(
            "kakaoScheme",
            if (kakaoKey.isBlank()) "runkk-kakao-disabled" else "kakao$kakaoKey"
        )
    }
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
    implementation(project(":data:phone"))
    implementation(project(":feature:home"))
    implementation(project(":feature:run"))
    implementation(project(":feature:record"))
    implementation(project(":feature:battle"))
    implementation(project(":feature:crew"))
    implementation(project(":feature:chat"))
    implementation(project(":feature:runimal"))
    implementation(project(":feature:avatar"))
    implementation(project(":feature:me"))
    implementation(project(":feature:notification"))
    implementation(project(":feature:share"))

    implementation(libs.androidx.core.ktx)
    // 카카오 SDK 초기화(KakaoSdkStartup, D-275). 로그인(v2-user)·공유(v2-share)와 같은 판이다.
    implementation(libs.kakao.sdk.common)
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
