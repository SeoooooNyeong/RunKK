package com.a207.runkk.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.navigation.FeatureEntry
import com.a207.runkk.app.domain.account.SessionRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var featureEntries: Set<@JvmSuppressWildcards FeatureEntry>

    /** 로그인 상태(5.1.2 app → domain.account: SessionRepository.loggedIn). 시작 경로와 로그아웃 처리에 쓴다. */
    @Inject
    lateinit var session: SessionRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RunkkTheme {
                // 첫 값을 받기 전에는 null(스플래시). StateFlow라 보통 첫 프레임에 바로 정해진다.
                val loggedIn by produceState<Boolean?>(initialValue = null, session) {
                    session.loggedIn.collect {
                        value =
                            it
                    }
                }
                RunkkAppContent(
                    featureEntries = featureEntries,
                    loggedIn = loggedIn,
                    onExit = ::finish,
                    // Modifier.testTag를 UI Automator 리소스 ID로 내보낸다(E2E Maestro 선택자 id:, android/e2e/maestro).
                    // debug 빌드에서만 켠다: release에서는 testTag를 접근성 트리에 리소스 ID로 내보이지 않는다(리뷰 N-1).
                    modifier = Modifier.semantics { testTagsAsResourceId = BuildConfig.DEBUG }
                )
            }
        }
    }
}
