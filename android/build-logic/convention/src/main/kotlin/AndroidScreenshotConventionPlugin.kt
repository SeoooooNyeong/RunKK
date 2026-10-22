import com.android.build.api.dsl.LibraryExtension
import io.github.takahirom.roborazzi.RoborazziExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType

/**
 * 화면 스크린샷 시험(Roborazzi, Robolectric 위 JVM). 화면 모듈(:core:ui, :feature:*)이 같이 쓴다.
 * - PNG는 모듈의 src/test/snapshots/에 둔다(저장소에 올려 시안과 비교).
 * - 기록: ./gradlew recordRoborazziLocalDebug, 비교: compareRoborazziLocalDebug, 검증: verifyRoborazziLocalDebug
 *   (라이브러리 모듈에는 플레이버가 없어 …Debug 작업에 잇는다).
 * - 시험 코드는 core:ui 시험 공용 도우미 RunkkSnapshot(captureRunkkScreen)으로 390×844dp·다크 기준을 맞춘다.
 */
class AndroidScreenshotConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("io.github.takahirom.roborazzi")
            extensions.configure<RoborazziExtension> {
                outputDir.set(layout.projectDirectory.dir("src/test/snapshots"))
            }
            extensions.configure<LibraryExtension> {
                testOptions.unitTests.isIncludeAndroidResources = true
            }
            tasks.withType<Test>().configureEach {
                // 실제 그리기(글꼴·그림자 포함)로 찍는다.
                systemProperty("robolectric.graphicsMode", "NATIVE")
                systemProperty("robolectric.pixelCopyRenderMode", "hardware")
                // 화면에 찍히는 시각은 기기 시간대로 바뀐다. 기준 이미지는 KST로 기록했으므로 UTC인 CI 컨테이너에서도 같게 고정한다.
                systemProperty("user.timezone", "Asia/Seoul")
            }
            dependencies {
                add("testImplementation", libs.lib("roborazzi"))
                add("testImplementation", libs.lib("roborazzi-compose"))
                add("testImplementation", libs.lib("roborazzi-junit-rule"))
                add("testImplementation", libs.lib("robolectric"))
                add("testImplementation", platform(libs.lib("androidx-compose-bom")))
                add("testImplementation", libs.lib("androidx-compose-ui-test-junit4"))
                add("debugImplementation", libs.lib("androidx-compose-ui-test-manifest"))
            }
            listOf("record", "compare", "verify").forEach { action ->
                tasks.register("${action}RoborazziLocalDebug") {
                    group = "verification"
                    description = "AGENTS.md 명령 이름에 맞춘 별칭: ${action}RoborazziDebug"
                    dependsOn("${action}RoborazziDebug")
                }
            }
        }
    }
}
