package com.a207.runkk.watch.run

import android.annotation.SuppressLint
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import com.a207.runkk.app.domain.run.LocationSample
import java.util.TreeMap
import kotlin.math.abs
import timber.log.Timber

/** 운동 위치와 독립적인 GPS 속도는 측정 시각이 가까운 좌표에만 적용한다. */
internal class WatchGnssSpeed(private val manager: LocationManager? = null) : LocationListener {
    private val readings = TreeMap<Long, Reading>()

    @SuppressLint("MissingPermission")
    fun start() {
        try {
            manager?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1_000L, 0f, this, Looper.getMainLooper())
        } catch (e: SecurityException) {
            Timber.w(e, "워치 GPS 속도 권한 없음")
        } catch (e: IllegalArgumentException) {
            Timber.w(e, "워치 GPS 속도 공급자 없음")
        }
    }

    fun pause() {
        manager?.removeUpdates(this)
    }

    fun stop() {
        pause()
        synchronized(this) { readings.clear() }
    }

    @Synchronized
    override fun onLocationChanged(location: Location) {
        if (!location.hasSpeed() || !location.hasSpeedAccuracy()) return
        val speed = location.speed.toDouble()
        val accuracy = location.speedAccuracyMetersPerSecond.toDouble()
        if (!speed.isFinite() || speed < 0 || !accuracy.isFinite() || accuracy < 0) return

        val at = location.elapsedRealtimeNanos / NANOS_PER_MILLI
        readings[at] = Reading(speed, accuracy)
        readings.headMap(at - HISTORY_MILLIS).clear()
    }

    @Synchronized
    fun applyTo(sample: LocationSample, elapsedRealtimeMillis: Long): LocationSample {
        val nearest = listOfNotNull(
            readings.floorEntry(elapsedRealtimeMillis),
            readings.ceilingEntry(elapsedRealtimeMillis)
        )
            .minByOrNull { abs(it.key - elapsedRealtimeMillis) }
            ?: return sample
        if (abs(nearest.key - elapsedRealtimeMillis) > MATCH_TOLERANCE_MILLIS) return sample

        val (speed, accuracy) = nearest.value
        return sample.copy(speedMps = speed, speedAccuracyMps = accuracy)
    }

    private data class Reading(val speed: Double, val accuracy: Double)

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
        const val HISTORY_MILLIS = 5 * 60_000L
        const val MATCH_TOLERANCE_MILLIS = 750L
    }
}
