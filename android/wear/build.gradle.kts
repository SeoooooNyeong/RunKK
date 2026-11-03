// 워치 앱(5.1.2 watch.heart·watch.link·watch.ui, 6.1.15). 폰 러닝의 동행 표시·심박 수집(C-002)에 더해, v0.21부터 domain과 공용 :data를
// 조립해 워치 단독 러닝을 측정·기록·전송한다(D-366). 폰 전용 :data:phone은 쓰지 않는다(D-365, Hilt 중복 바인딩).
// 의존 방향은 설계 v0.22 5절 표(워치 8줄)를 따른다. Application·Activity는 watch.ui, Hilt 바인딩은 구현이 있는 패키지의 …Bindings와
// watch.link의 WatchModule(link·heart·alert)이 한다(D-385).
plugins {
    alias(libs.plugins.runkk.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    // 상대 기록 미리 받기 파일(watch.cache OpponentRecordCache, JSON 한 개, D-377)
    alias(libs.plugins.kotlin.serialization)
    // 원형 화면 스크린샷(와이어프레임 K1~K5). 폰 화면 모듈의 runkk.android.screenshot은 라이브러리용이라 여기서 직접 붙인다.
    alias(libs.plugins.roborazzi)
}

android {
    // R은 화면 패키지(watch.ui)에 둔다: 5.1.2 의존 표상 watch.ui는 watch.link만 import한다(Konsist 구조 시험).
    namespace = "com.a207.runkk.watch.ui"

    defaultConfig {
        // Wearable Data Layer는 폰 앱과 같은 applicationId·서명으로 짝을 짓는다.
        applicationId = "com.a207.runkk"
        minSdk = 30
        // 폰과 같은 기준값에 1을 더해 Play 버전 코드를 분리한다.
        versionCode = providers.gradleProperty("runkk.versionCode").orElse("10").get().toInt() + 1
        versionName = "0.11.0"
    }

    buildFeatures {
        compose = true
        // RunkkWatchApp이 디버그 빌드에서만 Timber.DebugTree를 심는다.
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

// PNG는 src/test/snapshots/에 둔다. 기록: recordRoborazziLocalDebug, 검증: verifyRoborazziLocalDebug(폰 화면 모듈과 같은 이름).
roborazzi {
    outputDir.set(layout.projectDirectory.dir("src/test/snapshots"))
}

tasks.withType<Test>().configureEach {
    // 실제 그리기(글꼴 포함)로 찍는다(runkk.android.screenshot과 같다).
    systemProperty("robolectric.graphicsMode", "NATIVE")
    systemProperty("robolectric.pixelCopyRenderMode", "hardware")
}

dependencies {
    implementation(project(":wear-contract"))
    // 워치 단독 러닝(D-366): domain 클래스를 그대로 조립하고 공용 :data의 REST·실시간·보관함·전송·설정을 쓴다.
    implementation(project(":domain"))
    implementation(project(":data"))
    implementation(project(":core:model"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.wear.compose.foundation)
    implementation(libs.androidx.wear.compose.material3)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    // 워치 측정 서비스(watch.run WatchMeasurementService, LifecycleService, D-368)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.coroutines.guava)
    implementation(libs.androidx.health.services.client)
    implementation(libs.androidx.wear.ongoing)
    implementation(libs.play.services.wearable)
    implementation(libs.hilt.android)
    implementation(libs.timber)
    ksp(libs.hilt.compiler)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
