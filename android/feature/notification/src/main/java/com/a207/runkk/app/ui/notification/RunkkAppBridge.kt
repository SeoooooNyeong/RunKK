package com.a207.runkk.app.ui.notification

import android.webkit.JavascriptInterface

/**
 * 이벤트 웹 페이지에 window.RunkkApp으로 넣는 브리지(이벤트 계약 5절). 메서드는 웹뷰의 JavaBridge 스레드에서 불린다.
 *
 * 보안: 모든 메서드는 지금 웹뷰 주 문서 주소([currentUrl])의 origin이 [allowedOrigin](API origin)과 같을 때만 동작한다. 다른 origin이면
 * 토큰은 null, 나머지는 아무것도 하지 않는다. 화면이 다른 origin 이동과 다른 origin 자원 요청을 막으므로 주 문서는 늘 API origin이지만,
 * 이 검사가 마지막 방어선이다. 화면 동작([onOpen], [onClose], [onShare])은 [runOnMain]으로 주 스레드에 넘긴다.
 */
internal class RunkkAppBridge(
    private val allowedOrigin: String,
    private val currentUrl: () -> String?,
    private val accessToken: () -> String?,
    private val runOnMain: (() -> Unit) -> Unit,
    private val onOpen: (EventPageTarget) -> Unit,
    private val onClose: () -> Unit,
    private val onShare: (String) -> Unit
) {
    /** 지금 쓰는 접근 토큰. 믿을 수 없는 페이지거나 로그아웃 상태면 null. */
    @JavascriptInterface
    fun getAccessToken(): String? = if (isTrusted()) accessToken() else null

    /** "celebrity", "run", "events" 가운데 하나를 연다. 모르는 값은 무시한다. */
    @JavascriptInterface
    fun open(target: String?) {
        if (!isTrusted()) return
        val page = EventPageTarget.of(target) ?: return
        runOnMain { onOpen(page) }
    }

    @JavascriptInterface
    fun close() {
        if (!isTrusted()) return
        runOnMain(onClose)
    }

    /** 시스템 공유 시트. 빈 글은 무시하고 너무 긴 글은 자른다. */
    @JavascriptInterface
    fun share(text: String?) {
        if (!isTrusted()) return
        val body = text?.trim()?.take(MAX_SHARE_CHARS)?.takeIf { it.isNotEmpty() } ?: return
        runOnMain { onShare(body) }
    }

    /** 주 문서가 API origin인가. */
    internal fun isTrusted(): Boolean = isSameOrigin(currentUrl(), allowedOrigin)

    companion object {
        /** 페이지 JS에서 보이는 이름(window.RunkkApp). */
        const val NAME = "RunkkApp"

        private const val MAX_SHARE_CHARS = 2_000
    }
}
