package com.a207.runkk.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import com.a207.runkk.app.core.designsystem.AvatarCharacter
import com.a207.runkk.app.core.designsystem.LocalAvatarCharacter
import com.a207.runkk.app.core.designsystem.LocalKakaoMapKey
import com.a207.runkk.app.core.navigation.FeatureEntry
import com.a207.runkk.app.domain.account.AccountRepository
import com.a207.runkk.app.domain.account.SessionRepository
import com.a207.runkk.app.domain.settings.ScreenTheme
import com.a207.runkk.app.domain.settings.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var featureEntries: Set<@JvmSuppressWildcards FeatureEntry>

    /** 로그인 상태(5.1.2 app → domain.account: SessionRepository.loggedIn). 시작 경로와 로그아웃 처리에 쓴다. */
    @Inject
    lateinit var session: SessionRepository

    /** 설정 사본(5.1.2 app → domain.settings). 화면 테마(theme)를 구독해 RunkkTheme을 다시 그린다(FR-203, D-92). */
    @Inject
    lateinit var settings: SettingsRepository

    /** 내 성별(domain.account). 여성이면 기본 캐릭터를 여자로 그린다(LocalAvatarCharacter, 2026-10-30). */
    @Inject
    lateinit var account: AccountRepository

    /** 앱 키가 없으면 카카오 실행 링크를 받지 않는다(빈 스킴, 매니페스트도 쓰지 않는 스킴을 둔다). */
    private val inviteLinks =
        InviteLinkReader(BuildConfig.KAKAO_NATIVE_APP_KEY.takeIf { it.isNotBlank() }?.let { "kakao$it" }.orEmpty())

    /**
     * 초대 링크로 열렸을 때 아직 열지 않은 초대와 받은 시각(D-272, AN8-8). 회전·재생성에도 남도록 저장 상태에 둔다. 링크를 처음 받을 때만
     * 인텐트에서 읽고 읽은 링크는 인텐트에서 비운다(AN8-7). 다시 만들어질 때는 저장 상태를 쓴다(같은 인텐트로 초대를 두 번 열지 않게).
     * RunkkNavHost가 열거나 버리면 비운다.
     */
    private var pendingInvite by mutableStateOf<PendingInvite?>(null)

    /**
     * 대결 진행 알림을 눌러 열었을 때 돌아갈 대결 ID(S15P31A207-278). 인텐트에서 한 번 읽고 비운다. 대결 기능(앱 위 안내)이 그 대결 진행
     * 화면을 열거나 버리면 비운다.
     */
    private var pendingBattleReturn by mutableStateOf<Long?>(null)

    /**
     * 셀카 결과 알림을 눌러 열었을 때 얼굴 만들기 화면으로 갈 작업 ID([임시:D-성-146]). 인텐트에서 한 번 읽고 비운다. 로그인 뒤 홈 위에서
     * 얼굴 만들기를 열면 비운다.
     */
    private var pendingSelfieResult by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingInvite = if (savedInstanceState == null) takeInvite(intent) else restoreInvite(savedInstanceState)
        if (savedInstanceState == null) pendingBattleReturn = takeBattleReturn(intent)
        if (savedInstanceState == null) pendingSelfieResult = takeSelfieResult(intent)
        enableEdgeToEdge()
        setContent {
            // 저장된 화면 테마를 읽기 전(null)에는 아무것도 그리지 않고 창 배경을 둔다(A4: 첫 프레임에 SYSTEM 벌을 그렸다가 저장된
            // 벌로 바뀌는 깜빡임 방지). changes()는 기기 사본을 읽은 뒤 첫 값을 내고, 사본이 없으면 SYSTEM(기기 설정, D-162)이다.
            // 마지막 값은 저장해 두어 화면 회전 같은 Activity 재생성 때는 빈 프레임 없이 바로 그린다. 바뀌면 다시 그린다.
            var stored by rememberSaveable { mutableStateOf<ScreenTheme?>(null) }
            LaunchedEffect(settings) {
                settings.changes().map { it.theme }.distinctUntilChanged().collect { stored = it }
            }
            val theme = stored ?: return@setContent
            val isDark = isDarkTheme(theme, isSystemInDarkTheme())
            // 상태·내비게이션 막대 아이콘 색을 테마에 맞춘다(밝은 벌이면 어두운 아이콘).
            DisposableEffect(isDark) {
                val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { isDark }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            RunkkAppTheme(theme) {
                // 첫 값을 받기 전에는 null(스플래시). StateFlow라 보통 첫 프레임에 바로 정해진다.
                val loggedIn by produceState<Boolean?>(initialValue = null, session) {
                    session.loggedIn.collect { value = it }
                }
                // 로그아웃하면 내 성별 사본을 비운다(다음 회원이 이전 회원의 기본 캐릭터를 보지 않게, 리뷰 AN10-5)
                LaunchedEffect(loggedIn) { if (loggedIn == false) account.forgetMyGender() }
                val gender by account.myGender.collectAsState()
                val character = AvatarCharacter.of(gender)
                // Kakao 지도 키는 플레이버별 값(패키지마다 다른 네이티브 앱 키)을 화면 전체에 넣는다(LocalKakaoMapKey).
                CompositionLocalProvider(
                    LocalAvatarCharacter provides character,
                    LocalKakaoMapKey provides BuildConfig.KAKAO_NATIVE_APP_KEY
                ) {
                    RunkkAppContent(
                        featureEntries = featureEntries,
                        loggedIn = loggedIn,
                        onExit = ::finish,
                        pendingInvite = pendingInvite,
                        onInviteDone = { pendingInvite = null },
                        pendingBattleReturn = pendingBattleReturn,
                        onBattleReturnDone = { pendingBattleReturn = null },
                        pendingSelfieResult = pendingSelfieResult,
                        onSelfieResultDone = { pendingSelfieResult = null },
                        // Modifier.testTag를 UI Automator 리소스 ID로 내보낸다(E2E Maestro 선택자 id:, android/e2e/maestro).
                        // debug 빌드에서만 켠다: release에서는 testTag를 접근성 트리에 리소스 ID로 내보이지 않는다(리뷰 N-1).
                        modifier = Modifier.semantics { testTagsAsResourceId = BuildConfig.DEBUG }
                    )
                }
            }
        }
    }

    /** singleTask라 앱이 떠 있는 동안 온 초대 링크·대결 진행 알림·셀카 결과 알림 실행은 여기로 온다. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        takeInvite(intent)?.let { pendingInvite = it }
        takeBattleReturn(intent)?.let { pendingBattleReturn = it }
        takeSelfieResult(intent)?.let { pendingSelfieResult = it }
    }

    /** 셀카 결과 알림 실행이면 작업 ID를 읽고 인텐트에서 비운다. */
    private fun takeSelfieResult(intent: Intent): String? {
        val jobId = SelfieNoticeReader.read(intent) ?: return null
        setIntent(SelfieNoticeReader.consumed(getIntent()))
        return jobId
    }

    /** 대결 진행 알림 실행이면 대결 ID를 읽고 인텐트에서 비운다(S15P31A207-278). */
    private fun takeBattleReturn(intent: Intent): Long? {
        val battleId = BattleNoticeReader.read(intent) ?: return null
        setIntent(BattleNoticeReader.consumed(getIntent()))
        return battleId
    }

    /** 새로 받은 초대 링크를 읽고(최근 앱에서 다시 연 실행은 빼고) 인텐트에서 링크를 비운다(AN8-7). */
    private fun takeInvite(intent: Intent): PendingInvite? {
        val token = inviteLinks.readNew(intent) ?: return null
        setIntent(InviteLinkReader.consumed(intent))
        return PendingInvite(token, Instant.now())
    }

    private fun restoreInvite(saved: Bundle): PendingInvite? {
        val token = saved.getString(PENDING_INVITE) ?: return null
        return PendingInvite(token, Instant.ofEpochMilli(saved.getLong(PENDING_INVITE_AT)))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        pendingInvite?.let {
            outState.putString(PENDING_INVITE, it.token)
            outState.putLong(PENDING_INVITE_AT, it.receivedAt.toEpochMilli())
        }
    }

    private companion object {
        const val PENDING_INVITE = "runkk.pendingInvite"
        const val PENDING_INVITE_AT = "runkk.pendingInviteAt"
    }
}
