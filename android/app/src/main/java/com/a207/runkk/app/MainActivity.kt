package com.a207.runkk.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import com.a207.runkk.app.core.navigation.FeatureEntry
import com.a207.runkk.app.domain.account.SessionRepository
import com.a207.runkk.app.domain.settings.ScreenTheme
import com.a207.runkk.app.domain.settings.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
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

    private val inviteLinks = InviteLinkReader("kakao" + BuildConfig.KAKAO_NATIVE_APP_KEY)

    /**
     * 초대 링크로 열렸을 때 아직 열지 않은 초대 토큰(D-272). 회전·재생성에도 남도록 저장 상태에 둔다. 링크를 처음 받을 때만 인텐트에서 읽고,
     * 다시 만들어질 때는 저장 상태를 쓴다(같은 인텐트로 초대를 두 번 열지 않게). RunkkNavHost가 열면 비운다.
     */
    private var pendingInvite by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingInvite = if (savedInstanceState == null) {
            inviteLinks.read(intent)
        } else {
            savedInstanceState.getString(PENDING_INVITE)
        }
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
                RunkkAppContent(
                    featureEntries = featureEntries,
                    loggedIn = loggedIn,
                    onExit = ::finish,
                    pendingInvite = pendingInvite,
                    onInviteOpened = { pendingInvite = null },
                    // Modifier.testTag를 UI Automator 리소스 ID로 내보낸다(E2E Maestro 선택자 id:, android/e2e/maestro).
                    // debug 빌드에서만 켠다: release에서는 testTag를 접근성 트리에 리소스 ID로 내보이지 않는다(리뷰 N-1).
                    modifier = Modifier.semantics { testTagsAsResourceId = BuildConfig.DEBUG }
                )
            }
        }
    }

    /** singleTask라 앱이 떠 있는 동안 온 초대 링크는 여기로 온다. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        inviteLinks.read(intent)?.let { pendingInvite = it }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(PENDING_INVITE, pendingInvite)
    }

    private companion object {
        const val PENDING_INVITE = "runkk.pendingInvite"
    }
}
