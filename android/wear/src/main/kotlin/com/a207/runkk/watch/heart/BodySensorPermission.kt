package com.a207.runkk.watch.heart

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * OS 심박 권한. API 36(Wear OS 6)부터는 BODY_SENSORS 대신 health.READ_HEART_RATE를 받는다.
 * 권한 창은 화면(watch.ui의 Activity)이 PhoneLink를 거쳐 받은 [name]으로 띄우고, 결과는 HeartRateCollector.onPermissionChanged로 알린다.
 */
class BodySensorPermission @Inject constructor(@ApplicationContext private val context: Context) : SensorPermission {
    override val name: String get() = NAME

    override fun granted(): Boolean =
        ContextCompat.checkSelfPermission(context, NAME) == PackageManager.PERMISSION_GRANTED

    companion object {
        private const val READ_HEART_RATE = "android.permission.health.READ_HEART_RATE"
        private const val API_BAKLAVA = 36

        /** 이 기기에서 요청할 심박 권한 이름. */
        val NAME: String
            get() = if (Build.VERSION.SDK_INT >= API_BAKLAVA) READ_HEART_RATE else Manifest.permission.BODY_SENSORS
    }
}
