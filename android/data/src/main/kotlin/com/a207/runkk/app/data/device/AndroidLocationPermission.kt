package com.a207.runkk.app.data.device

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.a207.runkk.app.domain.run.LocationPermission
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** OS 정밀 위치 권한을 확인한다(6.1.14 data.device, FR-031). 권한 요청 화면은 ui.run이 띄운다. */
class AndroidLocationPermission
@Inject
constructor(@ApplicationContext private val context: Context) : LocationPermission {
    override fun granted(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
}
