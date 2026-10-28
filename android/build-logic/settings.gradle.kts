// 앱·워치 모듈의 공통 빌드 설정(convention plugin). 루트 settings.gradle.kts가 includeBuild로 불러온다.
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "build-logic"
include(":convention")
