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

/**
 * 심박을 모으는 동안 띄우는 포그라운드 서비스(Wear OS 운동 앱 권장, 타입 health). 워치 화면에는 Ongoing Activity로 보인다.
 * 수집은 PhoneLink·HeartRateCollector가 하고, 이 서비스는 그동안 프로세스를 살려 두기만 한다.
 * 수집이 멈추면([CollectionForegroundKeeper.awaitCollectionEnd]) 스스로 내려간다. 죽은 뒤 되살리지 않는다(START_NOT_STICKY):
 * 다음 폰 메시지가 프로세스를 띄우면 PhoneLink.start가 남은 운동 세션부터 끝낸다.
 */
@AndroidEntryPoint
class HeartRateCollectionService : Service() {
    @Inject
    lateinit var keeper: CollectionForegroundKeeper

    private val scope = MainScope()
    private var inForeground = false
    private var watcher: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!inForeground) {
            inForeground = enterForeground()
            if (!inForeground) {
                keeper.onServiceStopped()
                stopSelf(startId)
                return START_NOT_STICKY
            }
        }
        // 더 새 시작 요청이 오면 그 번호로 다시 기다린다(stopSelf(startId)는 마지막 요청일 때만 멈춘다).
        watcher?.cancel()
        watcher = scope.launch {
            keeper.awaitCollectionEnd()
            keeper.onServiceStopped()
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        keeper.onServiceStopped()
        if (inForeground) ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    /** 알림·Ongoing Activity를 붙여 포그라운드로 올린다. 권한이 없거나 시작이 막히면 거짓. */
    private fun enterForeground(): Boolean {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.collecting_channel), NotificationManager.IMPORTANCE_LOW)
        )
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, WatchActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val status = getString(R.string.collecting_status)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_heart)
            .setContentTitle(getString(R.string.collecting_title))
            .setContentText(status)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openApp)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        OngoingActivity.Builder(applicationContext, NOTIFICATION_ID, notification)
            .setStaticIcon(R.drawable.ic_stat_heart)
            .setTouchIntent(openApp)
            .setStatus(Status.Builder().addTemplate(status).build())
            .build()
            .apply(applicationContext)
        return try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification.build(), foregroundType())
            true
        } catch (_: SecurityException) {
            // health 타입의 선행 권한(심박 권한)이 없다. 수집은 권한이 있을 때만 켜지므로 드물다.
            false
        } catch (_: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException(API 31+): 백그라운드 시작이 막혔다. 화면이 앞으로 오면 다시 띄운다.
            false
        }
    }

    private fun foregroundType(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
    } else {
        0
    }

    private companion object {
        const val CHANNEL_ID = "heart_rate_collection"
        const val NOTIFICATION_ID = 1
    }
}
