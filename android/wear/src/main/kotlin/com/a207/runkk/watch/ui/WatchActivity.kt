package com.a207.runkk.watch.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.AmbientMode
import androidx.wear.compose.foundation.AmbientTickEffect
import androidx.wear.compose.foundation.rememberAmbientModeManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * 워치 화면을 띄운다. 단독 러닝 흐름은 [WatchFlowViewModel], 폰 러닝 동행 표시는 [WatchViewModel]이 상태를 낸다.
 *
 * 권한 창은 여기서만 띄운다.
 * - 위치(FR-245): 러닝을 시작하는 조작(달리기 시작·매칭 시작·셀프런 거리·셀럽 기록) 때 정밀 위치 권한이 없으면 묻는다. 처음에는 심박
 *   권한(심박 동의가 있으면 모은다, FR-041)과 알림 권한(진행 중 활동, FR-255)도 함께 묻는다. 위치를 거절하면 시작하지 않고 안내한다.
 *   워치 시스템 위치가 꺼져 있다는 안내를 누르면 위치 설정을 연다(W52). 신체 활동 권한(속도·케이던스, S15P31A207-242)은 위치와 함께,
 *   위치가 이미 있으면 화면이 열린 뒤 첫 시작 때 한 번 묻는다. 거절해도 시작한다.
 * - GPS 예열(W53): 앱 화면이 보이고(onResume~onPause) 저전력 표시가 아닐 때만 화면 흐름이 예열한다.
 * - 동행 표시 심박: 폰이 수집을 허용했는데 워치 OS 권한이 없으면 한 번 묻는다. 알림 권한은 심박 수집이 처음 켜질 때 한 번 묻는다.
 * 저전력 표시(ambient)에서는 측정 화면을 거리·시간만 1분마다 다시 그린다(D-382, FR-256). 폰 러닝 동행 표시도 같다(W35).
 */
@AndroidEntryPoint
class WatchActivity : ComponentActivity() {
    private val flowViewModel: WatchFlowViewModel by viewModels()
    private val companionViewModel: WatchViewModel by viewModels()

    @Inject
    lateinit var foregroundKeeper: CollectionForegroundKeeper

    /** 위치 권한 결과를 기다리는 시작 조작. */
    private var pendingStart: (() -> Unit)? = null

    /** 이 화면에서 신체 활동 권한을 이미 물었다(거절해도 시작마다 다시 묻지 않는다). */
    private var isActivityAsked = false

    private val startPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            val start = pendingStart
            pendingStart = null
            companionViewModel.onPermissionResult()
            if (results[Manifest.permission.ACCESS_FINE_LOCATION] == true || hasLocationPermission()) {
                start?.invoke()
            } else {
                flowViewModel.onLocationPermissionDenied()
            }
        }

    private val heartRatePermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            companionViewModel.onPermissionResult()
        }

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* 거절해도 다시 묻지 않는다 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val flow by flowViewModel.flow.collectAsStateWithLifecycle()
            val companion by companionViewModel.uiState.collectAsStateWithLifecycle()
            val ambient = rememberAmbientModeManager()
            var ambientTick by remember { mutableIntStateOf(0) }
            ambient.AmbientTickEffect { ambientTick++ }
            val isAmbient = ambient.currentAmbientMode is AmbientMode.Ambient
            LaunchedEffect(isAmbient) { flowViewModel.onAmbientChanged(isAmbient) }
            LaunchedEffect(companion.isNotificationPermissionNeeded) {
                if (companion.isNotificationPermissionNeeded) {
                    companionViewModel.onNotificationPromptShown()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }
            LaunchedEffect(companion.isHeartRatePermissionNeeded) {
                if (companion.isHeartRatePermissionNeeded) {
                    heartRatePermission.launch(
                        companionViewModel.heartRatePermission
                    )
                }
            }
            val actions = remember { actions() }
            WatchTheme {
                // 저전력 표시에서는 1분 박자(ambientTick)가 바뀔 때만 다시 그린다. 화면 종류가 바뀌면(결과 등) 바로 그린다.
                val shown = if (isAmbient) remember(ambientTick, flow::class) { flow } else flow
                val shownCompanion = if (isAmbient) remember(ambientTick, flow::class) { companion } else companion
                WatchRoot(flow = shown, companion = shownCompanion, actions = actions, isAmbient = isAmbient)
            }
        }
    }

    override fun onPause() {
        // 앱 화면이 가려지면 GPS 예열을 멈춘다(W53, NFR-048).
        flowViewModel.onScreenShown(false)
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        flowViewModel.onScreenShown(true)
        companionViewModel.onPermissionResult()
        // 백그라운드라 포그라운드 서비스를 못 띄운 채 수집 중이면 지금 띄운다.
        foregroundKeeper.onAppForeground()
    }

    private fun actions(): WatchActions = WatchActions(
        onModeSelected = flowViewModel::onModeSelected,
        onDistanceSelected = { distance ->
            // 셀프런 거리를 누르면 바로 시작한다(위치 권한 먼저). 자유런·배틀런·셀럽런 거리는 고르기만 한다.
            if (flowViewModel.flow.value is WatchFlow.SelfDistance) {
                withStartPermissions { flowViewModel.onDistanceSelected(distance) }
            } else {
                flowViewModel.onDistanceSelected(distance)
            }
        },
        onCelebritySelected = { celebrity -> withStartPermissions { flowViewModel.onCelebritySelected(celebrity) } },
        onStartPressed = { withStartPermissions(flowViewModel::onStartPressed) },
        onCancelMatching = flowViewModel::onCancelMatching,
        onPause = flowViewModel::onPause,
        onResume = flowViewModel::onResume,
        onEndTapped = flowViewModel::onEndTapped,
        onEndLongPressed = flowViewModel::onEndLongPressed,
        onResultClosed = flowViewModel::onResultClosed,
        onBack = flowViewModel::back,
        onCompanionEndTapped = companionViewModel::onEndTapped,
        onCompanionEndLongPressed = companionViewModel::onEndLongPressed,
        onCompanionPauseTapped = companionViewModel::onPauseTapped,
        onCompanionResumeTapped = companionViewModel::onResumeTapped,
        onOpenLocationSettings = ::openLocationSettings
    )

    /** 워치 위치 설정을 연다(W52). 위치 설정 화면이 없는 기기면 설정 첫 화면이다. */
    private fun openLocationSettings() {
        listOf(Settings.ACTION_LOCATION_SOURCE_SETTINGS, Settings.ACTION_SETTINGS).any { action ->
            try {
                startActivity(Intent(action))
                true
            } catch (_: ActivityNotFoundException) {
                false
            }
        }
    }

    /**
     * 정밀 위치 권한이 있으면 [start]를 바로, 없으면 권한 창(심박·알림·신체 활동 함께) 뒤에 부른다(FR-245 "처음 시작할 때 권한 창").
     * 위치가 있어도 신체 활동 권한이 없으면 이 화면에서 한 번은 권한 창 뒤에 부른다(결과와 관계없이 시작).
     */
    private fun withStartPermissions(start: () -> Unit) {
        val asks = startPermissionsToAsk(
            hasLocation = hasLocationPermission(),
            hasActivity = isGranted(Manifest.permission.ACTIVITY_RECOGNITION),
            isActivityAsked = isActivityAsked,
            heartRatePermission = companionViewModel.heartRatePermission,
            sdkInt = Build.VERSION.SDK_INT
        )
        if (asks.isEmpty()) {
            start()
            return
        }
        isActivityAsked = true
        pendingStart = start
        startPermissions.launch(asks.toTypedArray())
    }

    private fun hasLocationPermission(): Boolean = isGranted(Manifest.permission.ACCESS_FINE_LOCATION)

    private fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}
