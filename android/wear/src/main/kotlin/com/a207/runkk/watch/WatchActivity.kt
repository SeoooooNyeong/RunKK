package com.a207.runkk.watch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.watch.heart.BodySensorPermission
import com.a207.runkk.watch.ui.WatchRoot
import com.a207.runkk.watch.ui.WatchTheme
import com.a207.runkk.watch.ui.WatchViewModel
import dagger.hilt.android.AndroidEntryPoint

/**
 * 워치 화면을 띄운다. OS 심박 권한 창은 여기서만 띄운다: 사용자가 "심박 권한 허용"을 눌렀을 때만 요청하고,
 * 결과와 화면 복귀 때 다시 확인한다. 폰이 수집을 허용하지 않으면 권한이 있어도 모으지 않는다(NFR-023).
 */
@AndroidEntryPoint
class WatchActivity : ComponentActivity() {
    private val viewModel: WatchViewModel by viewModels()

    private val heartRatePermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.onPermissionResult() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            WatchTheme {
                WatchRoot(
                    state = state,
                    onStart = viewModel::onStartTapped,
                    onRequestPermission = { heartRatePermission.launch(BodySensorPermission.NAME) }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onPermissionResult()
    }
}
