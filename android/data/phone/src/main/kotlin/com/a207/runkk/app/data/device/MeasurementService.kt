package com.a207.runkk.app.data.device

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.Looper
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.GeoPoint
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.data.phone.R
import com.a207.runkk.app.domain.run.FreeRunGoalState
import com.a207.runkk.app.domain.run.LocationFilter
import com.a207.runkk.app.domain.run.LocationSample
import com.a207.runkk.app.domain.run.MeasurementHost
import com.a207.runkk.app.domain.run.MotionSample
import com.a207.runkk.app.domain.run.ProgressNotice
import com.a207.runkk.app.domain.run.RunTracker
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 측정 중 앱을 살려 두는 포그라운드 서비스(6.1.14 data.device, 타입 location, C-010, FR-035).
 * 업무 규칙이 없다: FusedLocationProvider 1초 구독을 RunTracker.onLocationSample로, 1초 시계를 RunTracker.tick으로 넘긴다(D-56).
 * 위치가 속도를 알려 주면 그 값을, 걸음 센서(StepCadenceSensor, 권한이 있을 때)의 케이던스를 RunTracker.onMotionSample로 넘긴다
 * (S15P31A207-242).
 * 잠금 화면 진행 알림(FR-224, D-259)은 RunTracker.notice를 구독해 같은 알림 ID로 다시 그리고, 표현은 시작할 때 OS 판으로 고른다
 * ([ProgressNotificationStyle.choose]). 알림의 일시정지·재개 버튼은 action과 함께 이 서비스를 부르고, 그때는 포그라운드 진입을
 * 다시 하지 않고 RunTracker의 알림 연산만 부른다(D-260). 위치·시각 값은 로그에 쓰지 않는다.
 */
@AndroidEntryPoint
class MeasurementService : LifecycleService() {
    @Inject
    lateinit var runTracker: RunTracker

    @Inject
    lateinit var clock: Clock

    private val locations by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private val steps by lazy { StepCadenceSensor(this) }
    private var ticking: Job? = null
    private var publishing: Job? = null
    private val style: ProgressNotificationStyle by lazy {
        ProgressNotificationStyle.choose(this, Build.VERSION.SDK_INT) {
            NotificationManagerCompat.from(this).canPostPromotedNotifications()
        }
    }

    private val callback =
        object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach { location ->
                    runTracker.onLocationSample(location.toSample())
                    location.toMotion()?.let(runTracker::onMotionSample)
                }
            }
        }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val isAction = routeAction(
            intent?.action,
            onPause = runTracker::onNotificationPauseTapped,
            onResume = runTracker::onNotificationResumeTapped
        )
        if (isAction) {
            // 측정이 끝난 뒤 남은 알림 버튼으로 깨어났으면 바로 내린다. 측정 중(시작 인텐트가 아직 처리되기 전 포함)이면 두지 않는다.
            if (ticking == null && runTracker.notice.value == null) stopSelf()
            return START_NOT_STICKY
        }
        if (!enterForeground() || !locationGranted()) {
            // 포그라운드로 오르지 못했거나 위치 권한이 없다: 측정 세션을 끝내(거리 0이면 저장하지 않음) 측정 중으로 남지 않게 한다.
            runTracker.endRun()
            stopSelf()
            return START_NOT_STICKY
        }
        startMeasuring()
        startPublishing()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        locations.removeLocationUpdates(callback)
        steps.stop()
        ticking?.cancel()
        publishing?.cancel()
        super.onDestroy()
    }

    /** 상단 알림과 함께 포그라운드로 올린다. OS가 거부하면(위치 권한 없음, 백그라운드 시작 제한) 거짓. */
    private fun enterForeground(): Boolean = try {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            firstNotification(),
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
        // 신체 활동 권한이 없으면 케이던스 없이 측정한다(서버가 비우고 ai가 no_cadence를 붙인다).
        steps.start(runTracker::onMotionSample)
        ticking =
            lifecycleScope.launch {
                while (isActive) {
                    delay(INTERVAL_MILLIS)
                    // 측정 시작 뒤에 신체 활동 권한을 허용했으면 이때부터 걸음을 잰다(MR !91 AN-4)
                    steps.ensureStarted(runTracker::onMotionSample)
                    runTracker.tick(clock.instant())
                }
            }
    }

    /** RunTracker.notice를 따라 같은 알림 ID로 다시 그린다. 측정이 끝나 null이면 알림을 내린다. */
    private fun startPublishing() {
        if (publishing != null) return
        val manager = NotificationManagerCompat.from(this)
        publishing =
            lifecycleScope.launch {
                followNotices(
                    runTracker.notice,
                    style,
                    post = { manager.notifyIfAllowed(it) },
                    remove = {
                        ServiceCompat.stopForeground(this@MeasurementService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    }
                )
            }
    }

    /** 알림 권한이 없으면(Android 13+ 거부) 갱신만 건너뛴다. 측정은 이어 간다(D-259). */
    @SuppressLint("MissingPermission") // areNotificationsEnabled로 먼저 확인한다.
    private fun NotificationManagerCompat.notifyIfAllowed(notification: Notification) {
        if (areNotificationsEnabled()) notify(NOTIFICATION_ID, notification)
    }

    /** 포그라운드 진입 알림: 채널(중요도 LOW)을 만들고 지금 진행 알림 값으로 첫 장을 그린다. */
    private fun firstNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.measurement_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
        )
        return style.build(runTracker.notice.value ?: EMPTY_NOTICE)
    }

    private fun Location.toSample(): LocationSample = LocationSample(
        at = Instant.ofEpochMilli(time),
        point = GeoPoint(latitude, longitude),
        accuracyMeters = if (hasAccuracy()) accuracy.toDouble() else Double.MAX_VALUE
    )

    /** 위치가 잰 속도(m/s). 속도를 모르거나 위치 정확도가 기준(30m)을 넘으면 null(서버가 위치로 셈한다). */
    private fun Location.toMotion(): MotionSample? =
        if (hasSpeed() && hasAccuracy() && accuracy <= LocationFilter.MAX_ACCURACY_METERS) {
            MotionSample(Instant.ofEpochMilli(time), speedMps = speed.toDouble())
        } else {
            null
        }

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

        /** 진행 알림 "일시정지" 버튼(D-260). */
        const val ACTION_PAUSE = "com.a207.runkk.app.action.MEASUREMENT_PAUSE"

        /** 진행 알림 "재개" 버튼(D-260). */
        const val ACTION_RESUME = "com.a207.runkk.app.action.MEASUREMENT_RESUME"

        /** 진행 알림 ID. 갱신은 모두 이 ID 하나로 한다. */
        const val NOTIFICATION_ID = 31

        /** 측정 알림 채널(중요도 LOW). */
        const val CHANNEL_ID = "runkk-measurement"
        private const val INTERVAL_MILLIS = 1_000L

        private val EMPTY_NOTICE: ProgressNotice =
            ProgressNotice.Free(Duration.ZERO, Distance.ZERO, FreeRunGoalState.None, isPaused = false)

        /**
         * 알림 버튼 action을 RunTracker 연산으로 넘긴다. 알림 버튼이면 참이고 포그라운드 진입을 다시 하지 않는다(TC-9616).
         * action이 없거나 모르는 값이면 거짓(측정 시작 요청)이다.
         */
        internal fun routeAction(action: String?, onPause: () -> Unit, onResume: () -> Unit): Boolean = when (action) {
            ACTION_PAUSE -> {
                onPause()
                true
            }

            ACTION_RESUME -> {
                onResume()
                true
            }

            else -> false
        }

        /**
         * 진행 알림 값마다 [post]로 다시 그리고, 그린 뒤 null(측정 끝·취소)이 오면 [remove]로 한 번 내린다.
         * 같은 값은 StateFlow가 거르고, 그리기 전의 null(측정 전)은 무시한다.
         */
        internal suspend fun followNotices(
            notices: Flow<ProgressNotice?>,
            style: ProgressNotificationStyle,
            post: (Notification) -> Unit,
            remove: () -> Unit
        ) {
            var isShown = false
            notices.collect { notice ->
                when {
                    notice != null -> {
                        post(style.build(notice))
                        isShown = true
                    }

                    isShown -> {
                        remove()
                        isShown = false
                    }
                }
            }
        }
    }
}
