// wear.contract: 폰(data.watch)과 워치(watch.link)가 함께 쓰는 메시지 형식(5.1.2, 6.1.15). 순수 Kotlin.
plugins {
    alias(libs.plugins.runkk.jvm.library)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
}
