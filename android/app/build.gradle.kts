plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ktlint)
}

android {
    namespace = "com.a207.runkk"
    // Latest AndroidX (Navigation 3 1.2, Hilt Navigation 1.4, Compose BOM 2026.09) requires compileSdk 37.
    // targetSdk stays 36 (Google Play requirement as of 2026-08-31).
    compileSdk = 37

    defaultConfig {
        applicationId = "com.a207.runkk"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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

    lint {
        abortOnError = true
        htmlReport = true
        xmlReport = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
    }

    // Environment flavors: BuildConfig.API_BASE_URL points at the matching backend profile.
    //   local   -> backend "local" profile on the developer PC (10.0.2.2 = host from the emulator)
    //   develop -> develop-branch server (replace the URL once the EC2 domain is assigned)
    //   prod    -> main-branch server
    flavorDimensions += "env"
    productFlavors {
        create("local") {
            dimension = "env"
            applicationIdSuffix = ".local"
            versionNameSuffix = "-local"
            buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:8080/\"")
        }
        create("develop") {
            dimension = "env"
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            buildConfigField("String", "API_BASE_URL", "\"https://dev.runkk.example/\"")
        }
        create("prod") {
            dimension = "env"
            buildConfigField("String", "API_BASE_URL", "\"https://runkk.example/\"")
        }
    }
}

dependencies {
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
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// 코드 컨벤션 4장: 포맷은 ktlint(official/android_studio 스타일, 규칙은 루트 .editorconfig)가 정한다. 커밋 전 ./gradlew ktlintFormat, CI는 ktlintCheck(check에 포함).
ktlint {
    version.set("1.8.0")
    android.set(true)
}
