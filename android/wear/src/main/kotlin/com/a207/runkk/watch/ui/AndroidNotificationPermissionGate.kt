package com.a207.runkk.watch.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

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
