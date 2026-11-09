pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Kakao SDK(로그인 C-007)는 Kakao 저장소에만 있다. com.kakao 묶음만 여기서 찾는다.
        maven("https://devrepo.kakao.com/nexus/content/groups/public/") {
            content { includeGroupByRegex("com\\.kakao.*") }
        }
    }
}

rootProject.name = "runkk"

// 설계서 13.2 Gradle 모듈. 설계 단위는 5.1.2의 패키지이고, 패키지 사이 방향은 :app의 Konsist 구조 시험이 지킨다.
include(":app")
include(":core:model")
include(":core:ui")
// 공용 Rive 아바타 렌더러(W9, S15P31A207-300). 화면 모듈(:feature:avatar 등)이 붙인다.
include(":core:avatar")
include(":domain")
include(":data")
// 폰 전용 data.device·data.watch·data.push(D-365, W16). :wear는 쓰지 않는다.
include(":data:phone")
include(":feature:home")
include(":feature:run")
include(":feature:record")
include(":feature:battle")
include(":feature:crew")
include(":feature:chat")
include(":feature:runimal")
include(":feature:avatar")
include(":feature:me")
include(":feature:notification")
include(":feature:share")
include(":wear")
include(":wear-contract")
