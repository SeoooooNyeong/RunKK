package com.a207.runkk.watch.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** [HeartRateCollectionService]를 포그라운드 서비스로 띄운다. */
class AndroidForegroundServiceLauncher @Inject constructor(@ApplicationContext private val context: Context) :
    ForegroundServiceLauncher {
    override fun launch(): Boolean = try {
        ContextCompat.startForegroundService(context, Intent(context, HeartRateCollectionService::class.java))
        true
    } catch (_: IllegalStateException) {
        // 백그라운드 시작 제한(API 31+ ForegroundServiceStartNotAllowedException).
        false
    } catch (_: SecurityException) {
        false
    }
}
