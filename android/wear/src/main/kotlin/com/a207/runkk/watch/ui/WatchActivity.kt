package com.a207.runkk.watch.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * 워치 화면을 띄운다. OS 심박 권한 창은 여기서만 띄운다: 사용자가 "심박 권한 허용"을 눌렀을 때만 요청하고,
 * 결과와 화면 복귀 때 다시 확인한다. 폰이 수집을 허용하지 않으면 권한이 있어도 모으지 않는다(NFR-023).
 * 같은 창에서 알림 권한(API 33+)도 묻는다: 수집 중임을 보이는 Ongoing Activity가 알림으로 뜨기 때문이다.
 */
@AndroidEntryPoint
class WatchActivity : ComponentActivity() {
    private val viewModel: WatchViewModel by viewModels()

    @Inject
    lateinit var foregroundKeeper: CollectionForegroundKeeper

    private val permissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            viewModel.onPermissionResult()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            WatchTheme {
                WatchRoot(
                    state = state,
                    onStart = viewModel::onStartTapped,
                    onRequestPermission = { permissions.launch(permissionsToRequest()) }
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

    private fun permissionsToRequest(): Array<String> {
        val heartRate = viewModel.heartRatePermission
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return arrayOf(heartRate)
        val notification = Manifest.permission.POST_NOTIFICATIONS
        val notificationGranted =
            ContextCompat.checkSelfPermission(this, notification) == PackageManager.PERMISSION_GRANTED
        return if (notificationGranted) arrayOf(heartRate) else arrayOf(heartRate, notification)
    }
}
