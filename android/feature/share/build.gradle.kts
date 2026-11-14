// ui.share: 공유 카드 이미지 만들기(RunCardRenderer·RunimalCardRenderer)와 공유(CardSharer·ShareTarget 3종)(5.1.2).
// 러니멀 유형 그림·이름 대응표, 랜드마크 배경, 카드 색(RunkkColors.dark)을 쓰려고 core.designsystem(:core:ui)에 의존한다(D-140).
// 카드 이미지는 Compose가 아니라 Android Canvas로 그린다.
// 카카오톡 링크 공유의 공통 코드(KakaoLinkSharer, 크루 초대 메시지)도 여기 둔다([임시:D-성-240]).
plugins {
    alias(libs.plugins.runkk.android.feature)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.runkk.android.screenshot)
}

android {
    namespace = "com.a207.runkk.app.ui.share"
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(project(":core:ui"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.hilt.android)
    implementation(libs.timber)
    // 카카오톡 공유(크루 초대 메시지의 기본 템플릿)
    implementation(libs.kakao.sdk.share)
    ksp(libs.hilt.compiler)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(testFixtures(project(":core:ui")))
    testImplementation(libs.androidx.test.core)
}
