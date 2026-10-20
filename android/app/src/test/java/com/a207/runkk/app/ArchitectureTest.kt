package com.a207.runkk.app

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 구조 시험(설계서 14장 "통합 — 사용 기반 (앱)", 13.2): 5.1.2 의존 표를 Konsist로 지킨다.
 * Gradle 모듈 사이 방향은 build-logic의 ModuleRules가, 같은 모듈 안 패키지 사이 방향은 이 시험이 막는다.
 * 규칙은 5.1.2 표 전체로 적어 두어 이후 단계(api.*, data.*, ui.*, watch.*)에서도 그대로 쓴다.
 */
class ArchitectureTest {
    private val files: List<KoFileDeclaration> =
        Konsist
            .scopeFromProduction()
            .files
            .filter { it.packageName().startsWith(ROOT) }

    @Test
    fun `검사할 파일을 찾는다`() {
        assertTrue(files.any { it.packageName().startsWith("$APP.domain.run") }, "domain.run 파일이 없다")
        assertTrue(files.any { it.packageName() == "$ROOT.wear.contract" }, "wear.contract 파일이 없다")
    }

    @Test
    fun `모든 파일은 5·1·2 표의 패키지 안에 있다`() {
        val outside =
            files
                .filter { moduleOf(it.packageName()) == null }
                .map { "${it.path} (${it.packageName()})" }
        assertEquals(emptyList(), outside)
    }

    @Test
    fun `5·1·2 표 밖 import 0`() {
        val violations = files.flatMap(::tableViolations).distinct()
        assertEquals(emptyList(), violations)
    }

    @Test
    fun `domain은 android·androidx(room 포함)·retrofit2·okhttp3·dagger(hilt 포함)를 import하지 않는다`() {
        val forbidden = listOf("android.", "androidx.", "retrofit2.", "okhttp3.", "dagger.")
        val violations =
            filesIn("domain.")
                .flatMap { file -> file.importNames().filter { name -> forbidden.any(name::startsWith) } }
        assertEquals(emptyList(), violations)
    }

    @Test
    fun `domain의 주입 애너테이션은 javax·inject만 쓴다 (D-61)`() {
        val others = listOf("jakarta.inject.", "com.google.inject.")
        val violations =
            filesIn("domain.")
                .flatMap { file -> file.importNames().filter { name -> others.any(name::startsWith) } }
        assertEquals(emptyList(), violations)
    }

    @Test
    fun `ui·app은 api·data를 import하지 않는다 (D-61)`() {
        val violations =
            files
                .filter { isAppOrUi(moduleOf(it.packageName())) }
                .flatMap { file -> file.importNames().filter(::isDataLayer) }
        assertEquals(emptyList(), violations)
    }

    @Test
    fun `GlobalScope를 쓰지 않는다`() {
        val violations = files.filter { it.text.contains("GlobalScope") }.map { it.path }
        assertEquals(emptyList(), violations)
    }

    private fun filesIn(modulePrefix: String): List<KoFileDeclaration> =
        files.filter { moduleOf(it.packageName())?.startsWith(modulePrefix) == true }

    private fun tableViolations(file: KoFileDeclaration): List<String> {
        val from = moduleOf(file.packageName()) ?: return emptyList()
        val allowed = ALLOWED.getValue(from)
        return file
            .importNames()
            .filter { it.startsWith(ROOT) }
            .mapNotNull { imported ->
                val to = moduleOf(imported.substringBeforeLast('.')) ?: moduleOf(imported)
                when {
                    to == null -> "$from → $imported (${file.name})"
                    to == from || to in allowed -> null
                    else -> "$from → $to (${file.name})"
                }
            }
    }

    private fun isAppOrUi(module: String?): Boolean = module == "app" || module?.startsWith("ui.") == true

    private fun isDataLayer(import: String): Boolean = import.startsWith("$APP.api.") || import.startsWith("$APP.data.")

    private fun KoFileDeclaration.packageName(): String = packagee?.name.orEmpty()

    private fun KoFileDeclaration.importNames(): List<String> = imports.map { it.name }

    private companion object {
        const val ROOT = "com.a207.runkk"
        const val APP = "$ROOT.app"
        const val WATCH = "$ROOT.watch"

        private val DOMAINS =
            listOf(
                "settings",
                "run",
                "sync",
                "live",
                "battle",
                "crew",
                "record",
                "runimal",
                "avatar",
                "notification",
                "account"
            )
        private val APIS =
            listOf("core", "account", "run", "record", "battle", "crew", "runimal", "avatar", "notification", "live")
        private val DATAS = listOf("store", "settings", "sync", "watch", "live", "device", "push", "session")
        private val UIS =
            listOf("share", "home", "run", "record", "battle", "crew", "runimal", "avatar", "me", "notification")

        /** 5.1.2 모듈(패키지) 이름 → 실제 패키지 접두어. 가장 긴 접두어가 이긴다. */
        val PACKAGES: Map<String, String> =
            buildMap {
                listOf("core.model", "core.navigation", "core.designsystem").forEach { put(it, "$APP.$it") }
                DOMAINS.forEach { put("domain.$it", "$APP.domain.$it") }
                APIS.forEach { put("api.$it", "$APP.api.$it") }
                DATAS.forEach { put("data.$it", "$APP.data.$it") }
                UIS.forEach { put("ui.$it", "$APP.ui.$it") }
                put("wear.contract", "$ROOT.wear.contract")
                listOf("heart", "link", "ui").forEach { put("watch.$it", "$WATCH.$it") }
            }

        private val CORE_UI = setOf("core.designsystem", "core.navigation", "core.model")

        /** 5.1.2 앱 모듈 사이 의존 표(코드 import 기준). 같은 모듈 안은 언제나 허용한다. */
        val ALLOWED: Map<String, Set<String>> =
            mapOf(
                "core.model" to emptySet(),
                "core.navigation" to emptySet(),
                "wear.contract" to emptySet(),
                "core.designsystem" to setOf("core.model"),
                "domain.settings" to setOf("core.model"),
                "domain.live" to setOf("core.model"),
                "domain.record" to setOf("core.model"),
                "domain.runimal" to setOf("core.model"),
                "domain.avatar" to setOf("core.model"),
                "domain.notification" to setOf("core.model"),
                "domain.run" to setOf("domain.settings", "core.model"),
                "domain.sync" to setOf("domain.run", "core.model"),
                "domain.account" to setOf("domain.run", "domain.settings", "core.model"),
                "domain.battle" to setOf("domain.run", "domain.live", "core.model"),
                "domain.crew" to setOf("domain.run", "domain.live", "core.model"),
                "api.core" to setOf("core.model"),
                "api.account" to setOf("api.core", "domain.account", "domain.settings", "core.model"),
                "api.run" to setOf("api.core", "domain.sync", "domain.run", "core.model"),
                "api.record" to setOf("api.core", "domain.record", "core.model"),
                "api.battle" to setOf("api.core", "domain.battle", "core.model"),
                "api.crew" to setOf("api.core", "domain.crew", "core.model"),
                "api.runimal" to setOf("api.core", "domain.runimal", "core.model"),
                "api.avatar" to setOf("api.core", "domain.avatar", "core.model"),
                "api.notification" to setOf("api.core", "domain.notification", "core.model"),
                "api.live" to setOf("api.core"),
                "data.store" to setOf("domain.run", "core.model"),
                "data.device" to setOf("domain.run", "core.model"),
                "data.settings" to setOf("domain.settings", "core.model"),
                "data.sync" to setOf("domain.sync", "domain.run", "core.model"),
                "data.watch" to setOf("domain.run", "domain.settings", "wear.contract"),
                "data.live" to setOf("domain.live", "api.core", "api.live", "core.model"),
                "data.push" to setOf("domain.notification"),
                "data.session" to setOf("domain.account", "domain.notification", "api.core"),
                "ui.share" to setOf("domain.record", "domain.runimal", "domain.settings", "core.model"),
                "ui.home" to CORE_UI + setOf("domain.record", "domain.run"),
                "ui.run" to CORE_UI + setOf("domain.run", "domain.record"),
                "ui.record" to CORE_UI + setOf("domain.record", "domain.run", "ui.share"),
                "ui.battle" to CORE_UI + setOf("domain.battle", "domain.live"),
                "ui.runimal" to CORE_UI + setOf("domain.runimal", "ui.share"),
                "ui.crew" to CORE_UI + "domain.crew",
                "ui.avatar" to CORE_UI + "domain.avatar",
                "ui.me" to CORE_UI + "domain.account",
                "ui.notification" to CORE_UI + "domain.notification",
                "app" to
                    setOf(
                        "domain.sync",
                        "domain.account",
                        "domain.settings",
                        "domain.notification",
                        "core.navigation",
                        "core.designsystem"
                    ),
                "watch.heart" to emptySet(),
                "watch.link" to setOf("wear.contract", "watch.heart"),
                "watch.ui" to setOf("watch.link")
            )

        /** 패키지 이름이 속한 5.1.2 모듈. app은 com.a207.runkk.app 바로 아래(하위 패키지 없음)만이다. */
        fun moduleOf(packageName: String): String? {
            if (packageName == APP) return "app"
            return PACKAGES.entries
                .filter { (_, prefix) -> packageName == prefix || packageName.startsWith("$prefix.") }
                .maxByOrNull { it.value.length }
                ?.key
        }
    }
}
