package com.a207.runkk.app.ui.notification

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.domain.notification.EventRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 이벤트 상세 웹 페이지(이벤트 계약 4절, 5절). 페이지를 열기 전에 내 참여(GET /events/{slug}/me)를 한 번 읽는다: 접근 토큰이 만료됐으면
 * 앱 HTTP 클라이언트가 이때 갱신해 두어, 페이지가 RunkkApp.getAccessToken()으로 받은 토큰이 바로 쓰인다. 결과와 상관없이 페이지를 연다.
 */
@HiltViewModel
class EventDetailViewModel @Inject constructor(private val events: EventRepository) : ViewModel() {
    private val _isReady = MutableStateFlow(false)

    /** 페이지를 열어도 되는가(토큰 확인이 끝났다). */
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private var slug: String? = null

    /** 브리지가 열릴 수 있는 origin(API origin). */
    val webOrigin: String get() = events.webOrigin()

    fun start(slug: String) {
        if (this.slug == slug) return
        this.slug = slug
        viewModelScope.launch {
            events.me(slug)
            _isReady.value = true
        }
    }

    fun accessToken(): String? = events.accessToken()
}
