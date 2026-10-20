// ui.record: 화면·ViewModel·FeatureEntry(5.1.2). 구현은 A3 단계에서 채운다.
plugins {
    alias(libs.plugins.runkk.android.feature)
}

android {
    namespace = "com.a207.runkk.app.ui.record"
}

dependencies {
    implementation(project(":feature:share"))
}
