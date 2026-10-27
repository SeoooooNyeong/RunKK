package com.a207.runkk.watch.ui

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * 워치 화면을 띄운다. OS 심박 권한 창은 여기서만 띄운다: 사용자가 "심박 권한 허용"을 눌렀을 때만 요청하고,
 * 결과와 화면 복귀 때 다시 확인한다. 폰이 수집을 허용하지 않으면 권한이 있어도 모으지 않는다(NFR-023).
 * 알림 권한(API 33+)은 따로 한 번만 묻는다: 심박 수집이 처음 켜질 때(수집 중임을 보이는 Ongoing Activity가 알림으로 뜬다).
 * 거절해도 다시 묻지 않고 수집은 계속한다.
 */
@AndroidEntryPoint
class WatchActivity : ComponentActivity() {
    private val viewModel: WatchViewModel by viewModels()

    @Inject
    lateinit var foregroundKeeper: CollectionForegroundKeeper

    private val heartRatePermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            viewModel.onPermissionResult()
        }

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* 거절해도 다시 묻지 않는다 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            LaunchedEffect(state.isNotificationPermissionNeeded) {
                if (state.isNotificationPermissionNeeded) {
                    viewModel.onNotificationPromptShown()
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            WatchTheme {
                WatchRoot(
                    state = state,
                    actions = remember {
                        WatchActions(
                            onStart = viewModel::onStartTapped,
                            onRequestPermission = { heartRatePermission.launch(viewModel.heartRatePermission) },
                            onReconnect = viewModel::onReconnectTapped,
                            onEndTapped = viewModel::onEndTapped,
                            onEndLongPressed = viewModel::onEndLongPressed,
                            onPauseTapped = viewModel::onPauseTapped,
                            onResumeTapped = viewModel::onResumeTapped
                        )
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onPermissionResult()
        // 백그라운드라 포그라운드 서비스를 못 띄운 채 수집 중이면 지금 띄운다.
        foregroundKeeper.onAppForeground()
    }
}
