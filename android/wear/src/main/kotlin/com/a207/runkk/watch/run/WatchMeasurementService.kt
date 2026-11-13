package com.a207.runkk.watch.run

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.run.MeasurementHost
import com.a207.runkk.app.domain.run.RunTracker
import com.a207.runkk.watch.ui.R
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * 워치 단독 측정 동안 포그라운드로 남아 표본을 RunTracker에 넘긴다(3.1, D-368, FR-256). 업무 규칙은 없다(폰 MeasurementService와 같다):
 * 운동 세션(WatchExerciseSource)의 위치를 RunTracker.onLocationSample로, 속도·케이던스를 RunTracker.onMotionSample로(S15P31A207-242),
 * 1초 시계를 RunTracker.tick으로, 심박을 LocalWatchGateway로
 * 넘기고, 자유런 일시정지·재개에 맞춰 운동 세션을 멈추고 잇는다([relay]).
 *
 * 타입은 location(늘)과 health(심박 권한이 있을 때)다. 측정은 앱 화면에서 시작하므로 백그라운드 위치 권한은 쓰지 않는다(C-022).
 * 진행 중 활동(Ongoing Activity, FR-255)으로 시계 화면·최근 앱에 "러닝 기록 중"과 누적 거리를 보이고, 누르면 앱으로 돌아간다.
 * 측정이 끝나면 RunTracker가 [Host.stop]으로 이 서비스를 내린다. 죽은 뒤 되살리지 않는다(START_NOT_STICKY).
 */
@AndroidEntryPoint
class WatchMeasurementService : LifecycleService() {
    @Inject
    lateinit var runTracker: RunTracker

    @Inject
    lateinit var source: WatchExerciseSource

    @Inject
    lateinit var gateway: LocalWatchGateway

    @Inject
    lateinit var clock: Clock

    private var ticking: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            // 시작 명령 뒤에 차례로 온다: 시작 명령이 포그라운드로 올린 뒤에 내리고 멈춘다(WR12-10). 이 명령 뒤에 새 시작 명령이
            // 와 있으면 멈추지 않는다(stopSelf(startId)).
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (ticking != null) return START_NOT_STICKY
        val ongoing = enterForeground()
        if (ongoing == null) {
            // 포그라운드로 오르지 못했다(위치 권한 없음, 백그라운드 시작 제한): 측정 중으로 남지 않게 끝낸다(거리 0이면 저장하지 않음).
            runTracker.endRun()
            stopSelf()
            return START_NOT_STICKY
        }
        ticking = lifecycleScope.launch {
            try {
                val failed = relay(runTracker, source, gateway, clock)
                Timber.w("운동 세션이 없어 측정을 끝낸다: %s", failed.error)
            } catch (e: CancellationException) {
                throw e
            } catch (e: RuntimeException) {
                // lifecycleScope에는 앱 범위의 예외 처리기가 없다: 표본 처리 오류로 프로세스가 죽지 않게 측정만 끝낸다.
                Timber.e(e, "측정 중 오류로 측정을 끝낸다")
            }
            runTracker.endRun()
            stopSelf()
        }
        lifecycleScope.launch { followDistance(ongoing) }
        return START_NOT_STICKY
    }

    /**
     * 누적 거리 표시가 바뀔 때마다 진행 중 활동 상태를 고친다(1초 tick마다 많아야 한 번, FR-255). 화면이 꺼졌거나 저전력 표시(ambient)면
     * [AMBIENT_UPDATE_MILLIS](1분, FR-256 제안)에 한 번만 고친다(D-382, NFR-048). 측정(위치 받기)은 화면 상태와 관계없이 이어 간다.
     * 알림 권한이 없으면(API 33+ 거부) 갱신만 건너뛴다. 측정은 이어 간다(폰 MeasurementService와 같다).
     */
    private suspend fun followDistance(ongoing: OngoingActivity) {
        val power = getSystemService(PowerManager::class.java)
        var shownAtMillis: Long? = null
        runTracker.progress
            .map { kmText(it.distance.meters) }
            .distinctUntilChanged()
            .collect { km ->
                val now = SystemClock.elapsedRealtime()
                if (!shouldUpdate(power?.isInteractive != false, shownAtMillis, now)) return@collect
                shownAtMillis = now
                try {
                    ongoing.update(this, status(km))
                } catch (_: SecurityException) {
                    // 알림 권한 없음
                }
            }
    }

    /** 알림·진행 중 활동을 붙여 포그라운드로 올린다. OS가 거부하면 null. */
    private fun enterForeground(): OngoingActivity? {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.measuring_channel), NotificationManager.IMPORTANCE_LOW)
        )
        val openApp = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_run)
            .setContentTitle(getString(R.string.measuring_title))
            .setContentText(getString(R.string.measuring_return))
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setOngoing(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        openApp?.let(notification::setContentIntent)
        val ongoing = OngoingActivity.Builder(applicationContext, NOTIFICATION_ID, notification)
            .setStaticIcon(R.drawable.ic_stat_run)
            .setStatus(status(kmText(runTracker.progress.value.distance.meters)))
            .apply { openApp?.let(::setTouchIntent) }
            .build()
        ongoing.apply(applicationContext)
        return try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification.build(), foregroundType())
            ongoing
        } catch (e: SecurityException) {
            Timber.w(e, "측정 서비스를 포그라운드로 올리지 못했다")
            null
        } catch (e: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException(API 31+)
            Timber.w(e, "측정 서비스를 포그라운드로 올리지 못했다")
            null
        }
    }

    private fun status(km: String): Status =
        Status.Builder().addTemplate(getString(R.string.measuring_status, km)).build()

    /** location은 늘, health는 심박 권한이 있을 때만(없이 health를 달면 OS가 시작을 거부한다). */
    private fun foregroundType(): Int {
        val location = ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return location
        return if (heartRatePermitted()) location or ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH else location
    }

    private fun heartRatePermitted(): Boolean {
        val name = if (Build.VERSION.SDK_INT >= API_BAKLAVA) READ_HEART_RATE else Manifest.permission.BODY_SENSORS
        return ContextCompat.checkSelfPermission(this, name) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * MeasurementHost 포트 구현: 이 서비스를 시작·정지한다. 서비스 객체는 OS가 만들므로 포트 구현은 이 중첩 클래스가 맡는다.
     * RunTracker가 어느 스레드에서 불러도 된다(시작·정지 요청은 OS가 메인 스레드로 넘긴다).
     */
    class Host @Inject constructor(@ApplicationContext private val context: Context) : MeasurementHost {
        /** OS가 시작을 거부하면 Failure(Rejected(MEASUREMENT_UNAVAILABLE)): RunTracker가 측정 시작을 되돌린다. */
        override fun startForeground(): Outcome<Unit> = try {
            ContextCompat.startForegroundService(context, Intent(context, WatchMeasurementService::class.java))
            Outcome.Success(Unit)
        } catch (_: IllegalStateException) {
            Outcome.Failure(AppError.Rejected(MEASUREMENT_UNAVAILABLE))
        } catch (_: SecurityException) {
            Outcome.Failure(AppError.Rejected(MEASUREMENT_UNAVAILABLE))
        }

        /**
         * 정지도 서비스 명령([ACTION_STOP])으로 보낸다(남은 위험 M5, 코드 리뷰 WR12-10). stopService는 시작 명령이 처리되기 전에 서비스를
         * 없애 포그라운드 5초 제한(startForeground 미호출)에 걸릴 수 있다. 명령은 시작 명령 뒤에 차례로 처리된다. 서비스를 시작할 수 없으면
         * (백그라운드 시작 제한) 그대로 멈춘다.
         */
        override fun stop() {
            val service = Intent(context, WatchMeasurementService::class.java)
            try {
                context.startService(Intent(service).setAction(ACTION_STOP))
            } catch (_: IllegalStateException) {
                context.stopService(service)
            } catch (_: SecurityException) {
                context.stopService(service)
            }
        }
    }

    companion object {
        /** 측정 서비스 정지 명령(Host.stop). */
        const val ACTION_STOP = "com.a207.runkk.watch.action.MEASUREMENT_STOP"

        /** 포그라운드 측정 서비스를 띄우지 못함(사유 코드, 폰 MeasurementService와 같은 값). */
        const val MEASUREMENT_UNAVAILABLE = "MEASUREMENT_UNAVAILABLE"

        /** 운동 세션이 이 앱 밖의 이유로 끝났다(사유 코드). */
        const val EXERCISE_ENDED = "EXERCISE_ENDED"

        private const val CHANNEL_ID = "watch_measurement"
        private const val NOTIFICATION_ID = 2
        private const val TICK_MILLIS = 1_000L
        private const val METERS_PER_KM = 1_000.0
        private const val API_BAKLAVA = 36
        private const val READ_HEART_RATE = "android.permission.health.READ_HEART_RATE"

        /** 저전력 표시(화면 꺼짐·ambient)일 때 진행 중 활동 상태를 고치는 간격(D-382, FR-256 제안 1분). */
        internal const val AMBIENT_UPDATE_MILLIS = 60_000L

        /** 진행 중 활동을 지금 고칠까: 화면이 켜져 있으면 늘, 저전력이면 마지막으로 고친 뒤 1분이 지났을 때만. */
        internal fun shouldUpdate(isInteractive: Boolean, shownAtMillis: Long?, nowMillis: Long): Boolean =
            isInteractive || shownAtMillis == null || nowMillis - shownAtMillis >= AMBIENT_UPDATE_MILLIS

        private fun kmText(meters: Long): String = String.format(Locale.ROOT, "%.2f", meters / METERS_PER_KM)

        /**
         * 운동 세션을 열고 표본을 RunTracker에 넘긴다. 세션을 열지 못하면 그 실패를, 세션이 밖에서 끝나면(다른 앱의 운동 등)
         * Rejected(EXERCISE_ENDED)를 돌려준다. 그 밖에는 취소될 때까지 돌아오지 않는다. 취소되거나 돌아올 때 세션을 끝낸다.
         * 표본 구독과 일시정지 구독을 먼저 걸어 세션의 첫 값을 놓치지 않는다.
         */
        internal suspend fun relay(
            tracker: RunTracker,
            source: WatchExerciseSource,
            gateway: LocalWatchGateway,
            clock: Clock
        ): Outcome.Failure = coroutineScope {
            launch(start = CoroutineStart.UNDISPATCHED) {
                source.locations().collect { tracker.onLocationSample(it, stabilize = true) }
            }
            launch(start = CoroutineStart.UNDISPATCHED) {
                source.heartRates().collect { gateway.offerHeartRates(listOf(it)) }
            }
            launch(start = CoroutineStart.UNDISPATCHED) { source.motions().collect(tracker::onMotionSample) }
            val ended = async(start = CoroutineStart.UNDISPATCHED) { source.ended.first() }
            val started = source.start()
            if (started is Outcome.Failure) {
                coroutineContext.cancelChildren()
                return@coroutineScope started
            }
            try {
                // 자유런 일시정지·재개를 운동 세션에 맞춘다(GPS 끔, NFR-048). 첫 값(시작 상태)은 건너뛴다.
                launch {
                    tracker.progress.map { it.isPaused }.distinctUntilChanged().drop(1).collect { isPaused ->
                        if (isPaused) source.pause() else source.resume()
                    }
                }
                launch { tickForever(tracker, clock) }
                ended.await()
                coroutineContext.cancelChildren()
                Outcome.Failure(AppError.Rejected(EXERCISE_ENDED))
            } finally {
                withContext(NonCancellable) { source.end() }
            }
        }

        /** 1초마다 RunTracker.tick(취소될 때까지). */
        private suspend fun tickForever(tracker: RunTracker, clock: Clock): Nothing {
            while (true) {
                delay(TICK_MILLIS)
                tracker.tick(clock.instant())
            }
        }
    }
}
