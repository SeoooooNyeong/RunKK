package com.a207.runkk.watch.run

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.a207.runkk.app.domain.run.LocationPermission
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * 워치 OS 정밀 위치 권한을 답한다(3.1, FR-245, FR-031). 권한 창은 화면(watch.ui)이 처음 시작할 때 띄운다.
 * 측정은 앱 화면에서 시작하므로 백그라운드 위치 권한은 쓰지 않는다(C-022, W05).
 */
class WatchLocationPermission @Inject constructor(@ApplicationContext private val context: Context) :
    LocationPermission {
    override fun granted(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
}
