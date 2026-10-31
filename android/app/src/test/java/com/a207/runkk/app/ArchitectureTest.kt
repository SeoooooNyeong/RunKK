package com.a207.runkk.app

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import com.lemonappdev.konsist.api.provider.KoNameProvider
import com.lemonappdev.konsist.api.provider.KoParentProvider
import com.lemonappdev.konsist.api.provider.modifier.KoSealedModifierProvider
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

    /** 12.4 구조 합격 조건(design-review 19장 R51·R52, D-335): 모듈 Fan-Out 7 이하, 허용 표 화살표 폰 150개·워치 5개. */
    @Test
    fun `모듈 Fan-Out은 7 이하이고 허용 표는 폰 150개·워치 5개다`() {
        val over = ALLOWED.filterValues { it.size > MAX_FAN_OUT }.mapValues { it.value.size }
        assertEquals(emptyMap(), over)
        val (watch, phone) = ALLOWED.entries.partition { it.key.startsWith("watch.") || it.key == "wear.contract" }
        assertEquals(150, phone.sumOf { it.value.size })
        assertEquals(5, watch.sumOf { it.value.size })
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
    fun `TC-6581 ui·chat은 domain·chat·domain·report·core만 import한다`() {
        assertEquals(CORE_UI + setOf("domain.chat", "domain.report"), ALLOWED.getValue("ui.chat"))
        assertTrue(files.any { moduleOf(it.packageName()) == "ui.chat" }, "ui.chat 파일이 없다")
        val violations = filesIn("ui.chat").flatMap(::tableViolations)
        assertEquals(emptyList(), violations)
    }

    @Test
    fun `TC-6582 domain·chat은 domain·live·core·model만, domain·report는 core·model만 import한다`() {
        assertEquals(setOf("domain.live", "core.model"), ALLOWED.getValue("domain.chat"))
        assertEquals(setOf("core.model"), ALLOWED.getValue("domain.report"))
        assertTrue(files.any { moduleOf(it.packageName()) == "domain.chat" }, "domain.chat 파일이 없다")
        assertTrue(files.any { moduleOf(it.packageName()) == "domain.report" }, "domain.report 파일이 없다")
        val violations =
            (filesIn("domain.chat") + filesIn("domain.report")).flatMap { file ->
                file.importNames().filter { name -> isDataLayer(name) || name.startsWith("$APP.ui.") } +
                    tableViolations(file)
            }
        assertEquals(emptyList(), violations)
    }

    @Test
    fun `TC-6583 한 파일 규칙은 sealed 계층과 짝 UseCase만 허용한다 (D-112, 픽스처)`() {
        fun fixture(name: String): List<String> =
            Konsist.scopeFromFile("$FIXTURES/$name.kt").files.flatMap(::oneFileViolations)
        assertEquals(emptyList(), fixture("SealedFixture"))
        assertEquals(emptyList(), fixture("PairedUseCaseFixture"))
        assertEquals(1, fixture("TwoClassesInTrackerFixture").size)
        assertEquals(1, fixture("TwoDataClassesFixture").size)
    }

    /**
     * 설계 검토 R59(C1): core.designsystem 안에서 재생(media3·android.media)과 기기 저장(SharedPreferences) import는 하위 패키지
     * core.designsystem.media에만 둔다. 나머지 디자인 시스템 부품은 그리기만 한다.
     */
    @Test
    fun `R59 디자인 시스템의 재생·기기 저장 import는 media 하위 패키지에만 있다`() {
        val media = "$APP.core.designsystem.media"
        assertTrue(files.any { it.packageName() == media }, "core.designsystem.media 파일이 없다")
        val violations = files
            .filter { moduleOf(it.packageName()) == "core.designsystem" && it.packageName() != media }
            .flatMap { file ->
                file.importNames()
                    .filter {
                        it.startsWith("androidx.media3.") || it.startsWith("android.media.") || it == SHARED_PREFS
                    }
                    .map { "${file.path} $it" }
            }
        assertEquals(emptyList(), violations)
    }

    /** D-112는 앱 전체 규칙이다. 도메인(A1)과 데이터 계층 api.*·data.*(A2)에 건다. 표현 계층은 아래 시험이 건다. */
    @Test
    fun `TC-6583 domain·api·data 소스는 한 파일 규칙을 지킨다 (D-112)`() {
        assertTrue(filesIn("api.").isNotEmpty() && filesIn("data.").isNotEmpty(), "api·data 파일이 없다")
        val violations = (filesIn("domain.") + filesIn("api.") + filesIn("data.")).flatMap(::oneFileViolations)
        assertEquals(emptyList(), violations)
    }

    /**
     * D-112를 표현 계층 전체로 넓힌다(A3a·A3b·A4): core.designsystem·core.navigation, app, 5.1.2 표의 ui.* 모듈 모두
     * (ui.battle·ui.runimal은 A4가 나눴다). 표에 ui 모듈이 새로 생기면 자동으로 이 검사에 들어간다.
     */
    @Test
    fun `TC-6583 core·app·ui 전체 소스는 한 파일 규칙을 지킨다 (D-112)`() {
        val modules = ONE_FILE_PRESENTATION_MODULES
        assertTrue("ui.battle" in modules && "ui.runimal" in modules && "ui.crew" in modules && "ui.chat" in modules)
        modules.forEach { module ->
            assertTrue(files.any { moduleOf(it.packageName()) == module }, "$module 파일이 없다")
        }
        val violations = files.filter { moduleOf(it.packageName()) in modules }.flatMap(::oneFileViolations)
        assertEquals(emptyList(), violations)
    }

    /** D-183: 한 파일 규칙의 적용 범위는 워치(:wear, 6.1.15 watch.*)까지다. */
    @Test
    fun `TC-6583 워치 소스도 한 파일 규칙을 지킨다 (D-112, D-183)`() {
        val watch = filesIn("watch.")
        assertTrue(watch.any { moduleOf(it.packageName()) == "watch.alert" }, "watch.alert 파일이 없다")
        assertEquals(emptyList(), watch.flatMap(::oneFileViolations))
    }

    @Test
    fun `TC-6584 domain은 한글 문자열 리터럴과 is·has·can이 아닌 Boolean 속성을 두지 않는다 (D-111)`() {
        assertEquals(emptyList(), filesIn("domain.").flatMap(::koreanLiterals))
        assertEquals(emptyList(), filesIn("domain.").flatMap(::booleanNameViolations))
        val fixture = Konsist.scopeFromFile("$FIXTURES/BooleanNameFixture.kt").files
        assertEquals(setOf("shouldFollow", "enabled"), fixture.flatMap(::booleanNameViolations).toSet())
    }

    @Test
    fun `TC-6585 data·push → domain·chat은 허용, domain·chat → data는 없다 (D-130)`() {
        assertTrue("domain.chat" in ALLOWED.getValue("data.push"))
        assertTrue(ALLOWED.getValue("domain.chat").none { it.startsWith("data.") })
    }

    @Test
    fun `GlobalScope를 쓰지 않는다`() {
        val violations = files.filter { it.text.contains("GlobalScope") }.map { it.path }
        assertEquals(emptyList(), violations)
    }

    @Test
    fun `AN8-4 화면을 바꿀 때 back() 뒤 navigate 대신 replace를 쓴다`() {
        val backThenNavigate = Regex("""\.back\(\)\s*\n\s*\w+\.navigate\(""")
        val violations = files.filter { backThenNavigate.containsMatchIn(it.text) }.map { it.path }
        assertEquals(emptyList(), violations)
    }

    /** D-112: 최상위 타입이 둘 이상이면 sealed 계층(부모와 하위 타입)이거나 private 함수를 나누는 짝 UseCase여야 한다. */
    private fun oneFileViolations(file: KoFileDeclaration): List<String> {
        val types: List<KoNameProvider> =
            file.classes(includeNested = false) + file.interfaces(includeNested = false) +
                file.objects(includeNested = false) + file.typeAliases
        if (types.size <= 1) return emptyList()
        val sealedRoots =
            types.filter { (it as? KoSealedModifierProvider)?.hasSealedModifier == true }.map { it.name }.toSet()
        val isSealedHierarchy =
            sealedRoots.isNotEmpty() &&
                types.all { type ->
                    type.name in sealedRoots ||
                        (type as? KoParentProvider)?.parents()?.any { it.name in sealedRoots } == true
                }
        val isPairedUseCase =
            types.all { it.name.endsWith("UseCase") } &&
                file.functions(includeNested = false).any { it.hasPrivateModifier }
        if (isSealedHierarchy || isPairedUseCase) return emptyList()
        return listOf("${file.path} ${types.map { it.name }}")
    }

    /** 주석을 뺀 소스에서 한글이 든 문자열 리터럴(D-111: 사용자 문구는 domain 밖 strings.xml). */
    private fun koreanLiterals(file: KoFileDeclaration): List<String> {
        val code = file.text.replace(BLOCK_COMMENT, "").replace(LINE_COMMENT, "")
        return STRING_LITERAL
            .findAll(code)
            .map { it.value }
            .filter { HANGUL.containsMatchIn(it) }
            .map { "${file.name}: $it" }
            .toList()
    }

    /** 형식을 적은 Boolean 속성(생성자 val·var 포함) 가운데 is·has·can으로 시작하지 않는 이름(D-111). */
    private fun booleanNameViolations(file: KoFileDeclaration): List<String> {
        val properties =
            file
                .properties(includeNested = true)
                .filter { it.type?.name == "Boolean" }
                .map { it.name }
        val parameters =
            file
                .classes(includeNested = true)
                .flatMap { it.primaryConstructor?.parameters.orEmpty() }
                .filter { (it.isVal || it.isVar) && it.type.name == "Boolean" }
                .map { it.name }
        return (properties + parameters).distinct().filterNot { BOOLEAN_PREFIX.matches(it) }
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

                    to == from || to in allowed || isE2eException(
                        file,
                        imported
                    ) || isOwnResources(from, imported) -> null

                    else -> "$from → $to (${file.name})"
                }
            }
    }

    /**
     * 표 밖 import의 유일한 예외(리뷰 N-3): localDebug 소스셋의 E2E 토큰 창구가 ui.me의 창구 자리를 쓴다.
     * 다른 변형에는 이 파일이 없다(E2eLoginPathTest, e2e/verify_release.sh).
     */
    private fun isE2eException(file: KoFileDeclaration, imported: String): Boolean =
        file.path.replace('\\', '/').contains("/src/localDebug/") &&
            file.name in E2E_TOKEN_FILES &&
            imported in E2E_TOKEN_IMPORTS

    /**
     * :data Gradle 모듈의 R(문자열 리소스)은 namespace 때문에 api.core 패키지에 생기지만 api.core 코드가 아니다.
     * data.*가 같은 Gradle 모듈의 리소스를 읽는 것은 표의 화살표로 치지 않는다(2차 리뷰 M-3: 도메인 안내 문구).
     */
    private fun isOwnResources(from: String, imported: String): Boolean =
        from.startsWith("data.") && imported == "$APP.api.core.R"

    private fun isAppOrUi(module: String?): Boolean = module == "app" || module?.startsWith("ui.") == true

    private fun isDataLayer(import: String): Boolean = import.startsWith("$APP.api.") || import.startsWith("$APP.data.")

    private fun KoFileDeclaration.packageName(): String = packagee?.name.orEmpty()

    private fun KoFileDeclaration.importNames(): List<String> = imports.map { it.name }

    private companion object {
        const val ROOT = "com.a207.runkk"
        const val APP = "$ROOT.app"
        const val WATCH = "$ROOT.watch"

        /** 기기 저장 import 이름(R59 C1). */
        const val SHARED_PREFS = "android.content.SharedPreferences"

        /** E2E 토큰 창구와 그 창구를 대체 자리에 넣는 Hilt 모듈(D-112로 파일을 나눴다). */
        val E2E_TOKEN_FILES = setOf("E2eKakaoTokenSource", "E2eLoginModule")
        val E2E_TOKEN_IMPORTS = setOf("$APP.ui.me.KakaoAccessTokenSource", "$APP.ui.me.KakaoTokenOverride")

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
                "account",
                "chat",
                "report"
            )
        private val APIS =
            listOf(
                "core",
                "account",
                "run",
                "record",
                "battle",
                "crew",
                "runimal",
                "avatar",
                "notification",
                "live",
                "chat",
                "report"
            )
        private val DATAS = listOf("store", "settings", "sync", "watch", "live", "device", "push", "session")
        private val UIS =
            listOf(
                "share",
                "home",
                "run",
                "record",
                "battle",
                "crew",
                "runimal",
                "avatar",
                "me",
                "notification",
                "chat"
            )

        /** 5.1.2 모듈(패키지) 이름 → 실제 패키지 접두어. 가장 긴 접두어가 이긴다. */
        val PACKAGES: Map<String, String> =
            buildMap {
                listOf("core.model", "core.navigation", "core.designsystem").forEach { put(it, "$APP.$it") }
                DOMAINS.forEach { put("domain.$it", "$APP.domain.$it") }
                APIS.forEach { put("api.$it", "$APP.api.$it") }
                DATAS.forEach { put("data.$it", "$APP.data.$it") }
                UIS.forEach { put("ui.$it", "$APP.ui.$it") }
                put("wear.contract", "$ROOT.wear.contract")
                listOf("heart", "alert", "link", "ui").forEach { put("watch.$it", "$WATCH.$it") }
            }

        private val CORE_UI = setOf("core.designsystem", "core.navigation", "core.model")

        /** 모듈 Fan-Out 상한(12.4, design-review 구조 합격 조건). */
        const val MAX_FAN_OUT = 7

        /** 한 파일 규칙(D-112)을 건 표현 계층 모듈: core 표현 두 패키지, app, 5.1.2 표의 ui.* 전부. */
        private val ONE_FILE_PRESENTATION_MODULES: List<String> =
            listOf("core.designsystem", "core.navigation", "app") + UIS.map { "ui.$it" }

        /** 한 파일 규칙·Boolean 이름 규칙의 픽스처(프로젝트 루트 기준). */
        const val FIXTURES = "app/src/test/resources/konsist"

        private val BLOCK_COMMENT = Regex("/\\*[\\s\\S]*?\\*/")
        private val LINE_COMMENT = Regex("//[^\\n]*")
        private val STRING_LITERAL = Regex("\"(?:[^\"\\\\\\n]|\\\\.)*\"")
        private val HANGUL = Regex("[\\uAC00-\\uD7A3]")
        private val BOOLEAN_PREFIX = Regex("(is|has|can)[A-Z].*")

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
                "domain.report" to setOf("core.model"),
                "domain.chat" to setOf("domain.live", "core.model"),
                "domain.notification" to setOf("core.model"),
                "domain.run" to setOf("domain.settings", "core.model"),
                // v0.17(D-332): 기기 사본 조정자 DeviceCopySync가 설정 사본(domain.settings), 푸시 토큰(domain.notification),
                // 오늘 계획(domain.record)을 서버와 맞춘다. 네 모듈 모두 domain.sync를 모른다(순환 0).
                "domain.sync" to
                    setOf("domain.run", "domain.settings", "domain.notification", "domain.record", "core.model"),
                "domain.account" to setOf("domain.run", "domain.settings", "core.model"),
                "domain.battle" to setOf("domain.run", "domain.live", "core.model"),
                "domain.crew" to setOf("domain.run", "domain.live", "core.model"),
                "api.core" to setOf("core.model"),
                "api.account" to setOf("api.core", "domain.account", "domain.settings", "core.model"),
                "api.run" to setOf("api.core", "domain.sync", "domain.run", "core.model"),
                "api.record" to setOf("api.core", "domain.record", "core.model"),
                // domain.live 추가: RecordBattleStart.track(OpponentTrack)은 OpponentTrack.from(TrackTable)로만 만든다(M39). A2 결정.
                "api.battle" to setOf("api.core", "domain.battle", "domain.live", "core.model"),
                "api.crew" to setOf("api.core", "domain.crew", "core.model"),
                "api.runimal" to setOf("api.core", "domain.runimal", "core.model"),
                "api.avatar" to setOf("api.core", "domain.avatar", "core.model"),
                "api.notification" to setOf("api.core", "domain.notification", "core.model"),
                "api.live" to setOf("api.core"),
                "api.chat" to setOf("api.core", "domain.chat", "core.model"),
                "api.report" to setOf("api.core", "domain.report", "core.model"),
                "data.store" to setOf("domain.run", "core.model"),
                "data.device" to setOf("domain.run", "core.model"),
                "data.settings" to setOf("domain.settings", "core.model"),
                "data.sync" to setOf("domain.sync", "domain.run", "core.model"),
                "data.watch" to setOf("domain.run", "domain.settings", "wear.contract"),
                "data.live" to setOf("domain.live", "api.core", "api.live", "core.model"),
                // domain.chat 추가: OpenChatRoomTracker가 OpenChatRoomRegistry를 구현한다(D-130).
                "data.push" to setOf("domain.notification", "domain.chat"),
                // core.model 추가: SessionRepository.save·clear가 Outcome을 돌려준다(13.2 "포트는 Outcome"). A2 결정.
                "data.session" to setOf("domain.account", "domain.notification", "api.core", "core.model"),
                // core.designsystem 추가: 러니멀 카드 이미지가 공용 유형 그림·이름 대응표(RunimalArt, RunimalAxisName)를 쓴다.
                // domain.crew 추가: 공동 목표 축하 카드(CelebrationCard, FR-141, D-140).
                "ui.share" to
                    setOf(
                        "domain.record",
                        "domain.runimal",
                        "domain.settings",
                        "domain.crew",
                        "core.model",
                        "core.designsystem"
                    ),
                // v0.14(D-276, D-277, 18장 146개 허용 표): 리워드 잔액은 대시보드 rewardBalance로 읽어 domain.avatar를 뺐고,
                // 첫 사용 가이드(FirstRunGuide) 때문에 domain.settings를 더했다. domain.account는 상단 프로필·위치 동의·로그인 상태다.
                "ui.home" to CORE_UI + setOf("domain.record", "domain.run", "domain.account", "domain.settings"),
                "ui.run" to CORE_UI + setOf("domain.run", "domain.record"),
                // domain.avatar 추가: 러닝 카드 배경 키·단계(AvatarRepository.backgroundChoice, D-140, D-185).
                "ui.record" to CORE_UI + setOf("domain.record", "domain.run", "domain.avatar", "ui.share"),
                // v0.14: domain.account는 위치 동의 시트(LocationConsentUseCase, D-267)와 본인 요약(MySummary: 초대 메시지의
                // 내 닉네임 D-270, 티어, 랜덤 대결 잠금의 유효 러닝 수 D-331), domain.record는 대결 결과 2쪽의 본인 기록 상세
                // (RunDetailSource, D-283)다. v0.17(D-331, R51): 유효 러닝 수를 본인 요약에서 받아 domain.runimal을 뺐다.
                "ui.battle" to CORE_UI + setOf("domain.battle", "domain.live", "domain.account", "domain.record"),
                "ui.runimal" to CORE_UI + setOf("domain.runimal", "ui.share"),
                // domain.report(신고 대화상자)·ui.share(축하 카드 공유) 추가(v0.11 F.6.4, D-140).
                "ui.crew" to CORE_UI + setOf("domain.crew", "domain.report", "ui.share"),
                "ui.chat" to CORE_UI + setOf("domain.chat", "domain.report"),
                "ui.avatar" to CORE_UI + "domain.avatar",
                // domain.settings는 프로필 편집의 출생연도(SettingsRepository.birthYear, D106)다(v0.16 5.1.2).
                "ui.me" to CORE_UI + setOf("domain.account", "domain.settings"),
                "ui.notification" to CORE_UI + "domain.notification",
                // core.model: 시작 작업(AppStartup)이 기기 사본 실패(AppError)를 기록한다. v0.17(D-332, R52): 시작·로그인 때
                // 기기 사본 맞추기는 domain.sync의 DeviceCopySync가 하고, 로그인 상태는 SessionRepository(domain.account)로 본다.
                // domain.settings는 화면 테마(ScreenTheme)다. domain.run·domain.record·domain.notification을 뺐다(Fan-Out 9 → 6).
                "app" to
                    setOf(
                        "core.model",
                        "domain.sync",
                        "domain.account",
                        "domain.settings",
                        "core.navigation",
                        "core.designsystem"
                    ),
                "watch.heart" to emptySet(),
                "watch.alert" to setOf("wear.contract"),
                "watch.link" to setOf("wear.contract", "watch.heart", "watch.alert"),
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
