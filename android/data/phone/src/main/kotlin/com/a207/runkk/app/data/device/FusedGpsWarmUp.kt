package com.a207.runkk.app.data.device

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.a207.runkk.app.core.model.GpsReadiness
import com.a207.runkk.app.domain.run.GpsWarmUp
import com.a207.runkk.app.domain.run.LocationPermission
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * GpsWarmUp 포트 구현(W55): 대결 성사 화면에서 출발 전에 GPS를 켠다. 측정 서비스(MeasurementService)와 같은 FusedLocationProvider
 * 1초 고정밀 요청이고, 모으기를 멈추면 요청을 푼다. 위치는 정확도만 보고 버린다(RunTracker에 넘기지 않아 기록에 남지 않는다).
 * 정확도 기준은 domain이 넘긴다. 위치 권한이 없으면 아무것도 내지 않고 끝난다(출발 때의 권한 안내가 맡는다).
 * 위치·시각 값은 로그에 쓰지 않는다.
 */
class FusedGpsWarmUp internal constructor(
    private val permission: LocationPermission,
    private val client: () -> FusedLocationProviderClient
) : GpsWarmUp {
    @Inject
    constructor(@ApplicationContext context: Context, permission: LocationPermission) : this(
        permission,
        client = { LocationServices.getFusedLocationProviderClient(context) }
    )

    @SuppressLint("MissingPermission") // permission.granted로 먼저 확인한다.
    override fun readiness(maxAccuracyMeters: Double): Flow<GpsReadiness> = callbackFlow {
        if (!permission.granted()) {
            close()
            return@callbackFlow
        }
        val locations = client()
        val callback =
            object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.locations.forEach { trySend(readinessOf(it, maxAccuracyMeters)) }
                }
            }
        send(GpsReadiness.SEARCHING)
        try {
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, INTERVAL_MILLIS).build()
            locations.requestLocationUpdates(request, callback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            // 확인한 뒤 권한이 거두어졌다: 예열 없이 끝낸다.
            close()
            return@callbackFlow
        }
        awaitClose { locations.removeLocationUpdates(callback) }
    }.distinctUntilChanged()

    internal companion object {
        /** 측정 서비스와 같은 1초 간격. */
        const val INTERVAL_MILLIS = 1_000L

        /** 정확도가 [maxAccuracyMeters] 이하인 위치면 준비됨. 정확도가 없는 위치는 찾는 중으로 본다(측정의 LocationFilter와 같다). */
        fun readinessOf(location: Location, maxAccuracyMeters: Double): GpsReadiness =
            if (location.hasAccuracy() && location.accuracy <= maxAccuracyMeters) {
                GpsReadiness.READY
            } else {
                GpsReadiness.SEARCHING
            }
    }
}
