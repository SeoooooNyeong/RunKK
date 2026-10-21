package com.a207.runkk.app.data.device

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.GeoPoint
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.run.LocationSample
import com.a207.runkk.app.domain.run.MeasurementHost
import com.a207.runkk.app.domain.run.RunTracker
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 측정 중 앱을 살려 두는 포그라운드 서비스(6.1.14 data.device, 타입 location, C-010, FR-035).
 * 업무 규칙이 없다: FusedLocationProvider 1초 구독을 RunTracker.onLocationSample로, 1초 시계를 RunTracker.tick으로 넘긴다(D-56).
 * 상단 알림을 띄우고, 위치·시각 값은 로그에 쓰지 않는다.
 */
@AndroidEntryPoint
class MeasurementService : LifecycleService() {
    @Inject
    lateinit var runTracker: RunTracker

    @Inject
    lateinit var clock: Clock

    private val locations by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private var ticking: Job? = null

    private val callback =
        object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach { runTracker.onLocationSample(it.toSample()) }
            }
        }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (!enterForeground() || !locationGranted()) {
            // 포그라운드로 오르지 못했거나 위치 권한이 없다: 측정 세션을 끝내(거리 0이면 저장하지 않음) 측정 중으로 남지 않게 한다.
            runTracker.endRun()
            stopSelf()
            return START_NOT_STICKY
        }
        startMeasuring()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        locations.removeLocationUpdates(callback)
        ticking?.cancel()
        super.onDestroy()
    }

    /** 상단 알림과 함께 포그라운드로 올린다. OS가 거부하면(위치 권한 없음, 백그라운드 시작 제한) 거짓. */
    private fun enterForeground(): Boolean = try {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        )
        true
    } catch (e: SecurityException) {
        false
    } catch (e: IllegalStateException) {
        // ForegroundServiceStartNotAllowedException(Android 12+)
        false
    }

    private fun locationGranted(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission") // onStartCommand가 locationGranted로 먼저 확인한다.
    private fun startMeasuring() {
        if (ticking != null) return
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, INTERVAL_MILLIS).build()
        locations.requestLocationUpdates(request, callback, Looper.getMainLooper())
        ticking =
            lifecycleScope.launch {
                while (isActive) {
                    delay(INTERVAL_MILLIS)
                    runTracker.tick(clock.instant())
                }
            }
    }

    private fun notification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW)
        )
        val open =
            packageManager.getLaunchIntentForPackage(packageName)?.let {
                PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE)
            }
        return NotificationCompat
            .Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle(NOTIFICATION_TITLE)
            .setContentText(NOTIFICATION_TEXT)
            .setOngoing(true)
            .setContentIntent(open)
            .build()
    }

    private fun Location.toSample(): LocationSample = LocationSample(
        at = Instant.ofEpochMilli(time),
        point = GeoPoint(latitude, longitude),
        accuracyMeters = if (hasAccuracy()) accuracy.toDouble() else Double.MAX_VALUE
    )

    /**
     * MeasurementHost 포트 구현: 이 서비스를 시작·정지한다. 서비스 객체는 OS가 만들므로 포트 구현은 이 중첩 클래스가 맡는다.
     * RunTracker가 어느 스레드(applicationScope 디스패처 포함)에서 불러도 된다: 시작·정지 요청은 OS가 메인 스레드로 넘긴다.
     */
    class Host
    @Inject
    constructor(@ApplicationContext private val context: Context) : MeasurementHost {
        /** OS가 시작을 거부하면 Failure(Rejected(MEASUREMENT_UNAVAILABLE)): RunTracker가 측정 시작을 되돌린다. */
        override fun startForeground(): Outcome<Unit> = try {
            ContextCompat.startForegroundService(context, Intent(context, MeasurementService::class.java))
            Outcome.Success(Unit)
        } catch (e: IllegalStateException) {
            // 앱이 백그라운드라 시작할 수 없다(Android 12+ ForegroundServiceStartNotAllowedException).
            Outcome.Failure(AppError.Rejected(MEASUREMENT_UNAVAILABLE))
        } catch (e: SecurityException) {
            // 위치 권한 없음: 보통은 RunTracker.checkStartable이 먼저 막는다.
            Outcome.Failure(AppError.Rejected(MEASUREMENT_UNAVAILABLE))
        }

        override fun stop() {
            context.stopService(Intent(context, MeasurementService::class.java))
        }
    }

    companion object {
        /** 포그라운드 측정 서비스를 띄우지 못함(사유 코드). */
        const val MEASUREMENT_UNAVAILABLE = "MEASUREMENT_UNAVAILABLE"
        private const val NOTIFICATION_ID = 31
        private const val CHANNEL_ID = "runkk-measurement"
        private const val CHANNEL_NAME = "러닝 측정"
        private const val NOTIFICATION_TITLE = "러닝 측정 중"
        private const val NOTIFICATION_TEXT = "화면을 꺼도 거리와 시간을 계속 잽니다"
        private const val INTERVAL_MILLIS = 1_000L
    }
}
