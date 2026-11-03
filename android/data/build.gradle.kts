// api.*·data.*: 도메인 포트 구현(5.1.2, 6.1.14 "데이터"). 포트 ↔ 구현 바인딩은 패키지마다 …Bindings가 한다(D-61).
// 폰과 워치가 함께 쓰는 공용 모듈이다(D-365, D-366). 폰 전용 data.device·data.watch·data.push는 :data:phone에 있다.
plugins {
    alias(libs.plugins.runkk.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// 배포 서버(EC2, infra/edge.env SERVER_NAME). develop·prod 플레이버의 기본 주소다.
// 한 서버의 nginx가 포트로 환경을 나눈다(infra/nginx/https): 443 → prod 환경(main 브랜치), 8443 → develop 환경(develop 브랜치).
val serverHost = "k15a207.p.ssafy.io"
val developServer = "$serverHost:8443"

android {
    // BuildConfig(플레이버별 서버 주소)가 api.core 패키지에 생기게 한다. 서버 주소는 api.core만 읽는다(5.1.2 의존 표).
    namespace = "com.a207.runkk.app.api.core"

    buildFeatures {
        buildConfig = true
    }

    // 앱(:app)과 같은 env 플레이버. 이름이 같아 앱의 localDebug가 이 모듈의 localDebug를 고른다.
    //   API_BASE_URL    REST 기준 주소(5.7 "모든 엔드포인트는 /api/v1 아래")
    //   LIVE_SOCKET_URL 실시간 WebSocket(5.7.2 경로 /ws)
    flavorDimensions += "env"
    productFlavors {
        create("local") {
            dimension = "env"
            // 에뮬레이터에서 개발 PC의 backend local 프로필(10.0.2.2 = 호스트). 빌드 속성 RUNKK_LOCAL_API_URL·RUNKK_LOCAL_WS_URL
            // (local.properties·-P·환경 변수)이 있으면 그 주소로 덮는다. E2E 시험(e2e/README.md)은 8090 포트 서버를 쓴다.
            val localApiUrl = secretProperty("RUNKK_LOCAL_API_URL").ifEmpty { "http://10.0.2.2:8080/api/v1/" }
            val localWsUrl = secretProperty("RUNKK_LOCAL_WS_URL").ifEmpty { "ws://10.0.2.2:8080/ws" }
            buildConfigField("String", "API_BASE_URL", buildConfigString(localApiUrl))
            buildConfigField("String", "LIVE_SOCKET_URL", buildConfigString(localWsUrl))
        }
        // develop·prod는 EC2(nginx HTTPS)를 쓴다. develop 플레이버는 develop 서버(8443), prod 플레이버는 베타 서버(443).
        // 다른 서버로 붙이려면 빌드 속성 RUNKK_DEVELOP_API_URL·RUNKK_DEVELOP_WS_URL·RUNKK_PROD_API_URL·RUNKK_PROD_WS_URL
        // (local.properties·-P·환경 변수)로 덮는다.
        create("develop") {
            dimension = "env"
            val apiUrl = secretProperty("RUNKK_DEVELOP_API_URL").ifEmpty { "https://$developServer/api/v1/" }
            val wsUrl = secretProperty("RUNKK_DEVELOP_WS_URL").ifEmpty { "wss://$developServer/ws" }
            buildConfigField("String", "API_BASE_URL", buildConfigString(apiUrl))
            buildConfigField("String", "LIVE_SOCKET_URL", buildConfigString(wsUrl))
        }
        create("prod") {
            dimension = "env"
            val apiUrl = secretProperty("RUNKK_PROD_API_URL").ifEmpty { "https://$serverHost/api/v1/" }
            val wsUrl = secretProperty("RUNKK_PROD_WS_URL").ifEmpty { "wss://$serverHost/ws" }
            buildConfigField("String", "API_BASE_URL", buildConfigString(apiUrl))
            buildConfigField("String", "LIVE_SOCKET_URL", buildConfigString(wsUrl))
        }
    }

    testOptions {
        unitTests {
            // Robolectric(Room 메모리 DB, WorkManager 시험 도구)이 리소스·매니페스트를 읽는다.
            isIncludeAndroidResources = true
        }
    }

    // Room 이전 시험(MigrationTestHelper)이 내보낸 스키마를 자산으로 읽는다.
    sourceSets {
        getByName("test").assets.directories.add("$projectDir/schemas")
    }
}

// 1차 배포 잠금(D-251, D-339): 앱(:app)의 LOCK_UNRELEASED와 같은 규칙이다. prod·develop은 참, local은 거짓이고 Gradle 속성
// -Prunkk.lockUnreleased가 있으면 그 값이다. 시스템 푸시(RunkkMessagingService)가 닫힌 기능의 알림을 띄우지 않을 때 읽는다.
val lockUnreleasedOverride = providers.gradleProperty("runkk.lockUnreleased").orNull?.toBooleanStrictOrNull()
androidComponents {
    onVariants { variant ->
        val local = variant.productFlavors.any { it.second == "local" }
        val lock = lockUnreleasedOverride ?: !local
        variant.buildConfigFields?.put(
            "LOCK_UNRELEASED",
            com.android.build.api.variant.BuildConfigField("boolean", lock.toString(), "1차 배포에서 열지 않는 기능을 막는가")
        )
    }
}

ksp {
    // Room 스키마를 저장소에 남겨 이후 이전(migration) 시험의 기준으로 쓴다.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":core:model"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.timber)

    // Hilt: 바인딩 모듈
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // api.*: 서버 연결
    implementation(libs.okhttp)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)

    // data.store·data.settings·data.sync·data.session
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.security.crypto)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.work.testing)
}
