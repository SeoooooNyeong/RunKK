// :data:phone(D-365, 사용자 결정 W16): 폰에서만 쓰는 data.device(측정 서비스·안내 출력·위치 권한·진행 알림), data.watch(워치 통신),
// data.push(FCM)를 공용 :data에서 뗀 모듈이다. 패키지 이름은 그대로이고(5.1.2), 워치 앱(:wear)은 이 모듈을 쓰지 않는다(Hilt 중복 바인딩).
plugins {
    alias(libs.plugins.runkk.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    // R(폰 전용 문구)이 생기는 자리다. 5.1.2 표의 패키지가 아니므로 구조 시험은 data.*가 읽는 같은 모듈 리소스로만 본다.
    namespace = "com.a207.runkk.app.data.phone"

    // :data와 같은 env 플레이버. 이름이 같아 앱의 localDebug가 이 모듈과 :data의 localDebug를 고른다.
    flavorDimensions += "env"
    productFlavors {
        create("local") { dimension = "env" }
        create("develop") { dimension = "env" }
        create("prod") { dimension = "env" }
    }

    testOptions {
        unitTests {
            // Robolectric(진행 알림·문구 시험)이 리소스·매니페스트를 읽는다.
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(project(":data"))
    implementation(project(":domain"))
    implementation(project(":core:model"))
    implementation(project(":wear-contract"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.timber)

    // Hilt: 바인딩 모듈, 측정·푸시 서비스(@AndroidEntryPoint)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // data.device·data.watch·data.push
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.play.services.location)
    implementation(libs.play.services.wearable)
    implementation(libs.firebase.messaging)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
