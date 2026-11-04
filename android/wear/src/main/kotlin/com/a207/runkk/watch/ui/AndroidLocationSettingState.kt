package com.a207.runkk.watch.ui

import android.content.Context
import android.location.LocationManager
import androidx.core.location.LocationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class AndroidLocationSettingState @Inject constructor(@ApplicationContext private val context: Context) :
    LocationSettingState {
    /** 위치 서비스를 얻지 못하면 막지 않는다(Health Services가 판정한다). */
    override fun isEnabled(): Boolean =
        context.getSystemService(LocationManager::class.java)?.let(LocationManagerCompat::isLocationEnabled) ?: true
}
