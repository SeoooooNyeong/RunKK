package com.a207.runkk.watch.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * 알림 권한(API 33+)을 물어야 하는지 정하는 포트. 구현 [AndroidNotificationPermissionGate].
 * 심박 수집이 처음 켜질 때 한 번만 묻는다. 거절해도 다시 묻지 않고 수집은 계속한다.
 */
interface NotificationPermissionGate {
    /** 지금 물어야 하면 참: 알림 권한이 필요한 OS이고, 아직 허용되지 않았고, 이전에 물은 적이 없다. */
    fun shouldAsk(): Boolean

    /** 물었다고 기록한다. 이후 [shouldAsk]는 거짓이다(앱을 다시 켜도). */
    fun markAsked()
}

class AndroidNotificationPermissionGate @Inject constructor(@ApplicationContext private val context: Context) :
    NotificationPermissionGate {
    private val preferences get() = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    override fun shouldAsk(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED &&
        !preferences.getBoolean(KEY_ASKED, false)

    override fun markAsked() {
        preferences.edit().putBoolean(KEY_ASKED, true).apply()
    }

    private companion object {
        const val PREFERENCES = "notification_prompt"
        const val KEY_ASKED = "asked"
    }
}
