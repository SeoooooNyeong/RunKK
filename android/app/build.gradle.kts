plugins {
    alias(libs.plugins.runkk.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
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
            isMinifyEnabled = false
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

    // Environment flavors (runkk.android.application이 env 차원을 만든다): BuildConfig.API_BASE_URL points at the
    // matching backend profile.
    //   local   -> backend "local" profile on the developer PC (10.0.2.2 = host from the emulator)
    //   develop -> develop-branch server (replace the URL once the EC2 domain is assigned)
    //   prod    -> main-branch server
    productFlavors {
        getByName("local") {
            buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:8080/\"")
        }
        getByName("develop") {
            buildConfigField("String", "API_BASE_URL", "\"https://dev.runkk.example/\"")
        }
        getByName("prod") {
            buildConfigField("String", "API_BASE_URL", "\"https://runkk.example/\"")
        }
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
    ksp(libs.hilt.compiler)

    // 구조 시험(14장): 5.1.2 의존 표·domain 순수성 규칙을 Konsist로 확인한다.
    testImplementation(libs.konsist)
    // 시작 경로·하단 탭 시험(Robolectric Compose)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.robolectric)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
