// ui.home: 홈 화면·ViewModel·FeatureEntry(5.1.2).
plugins {
    alias(libs.plugins.runkk.android.feature)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.a207.runkk.app.ui.home"
}

dependencies {
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}
