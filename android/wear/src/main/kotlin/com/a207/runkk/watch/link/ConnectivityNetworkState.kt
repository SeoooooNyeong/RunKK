package com.a207.runkk.watch.link

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ConnectivityManager 기본 네트워크의 인터넷 가능 여부로 [NetworkState]를 구현한다(J-6). 앱 프로세스 동안 기본 네트워크 콜백 하나를 건다.
 * 실제 요청 실패는 이 값과 관계없이 각 흐름의 규칙(FR-090·FR-085)을 따른다.
 */
@Singleton
class ConnectivityNetworkState @Inject constructor(@ApplicationContext context: Context) : NetworkState {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    private val _isOnline = MutableStateFlow(currentlyOnline())
    override val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    init {
        connectivity?.registerDefaultNetworkCallback(
            object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                    _isOnline.value = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                }

                override fun onLost(network: Network) {
                    _isOnline.value = false
                }
            }
        )
    }

    private fun currentlyOnline(): Boolean {
        val manager = connectivity ?: return false
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
