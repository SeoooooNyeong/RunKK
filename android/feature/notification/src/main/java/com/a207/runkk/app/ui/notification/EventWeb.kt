package com.a207.runkk.app.ui.notification

import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

/**
 * 주소의 origin(scheme://host[:port], 기본 포트는 뺀다). http·https가 아니거나 호스트가 없거나 읽을 수 없으면 null이다.
 * 웹뷰 브리지와 이동 막기가 이 값을 API origin(EventRepository.webOrigin)과 비교한다.
 */
internal fun webOriginOf(url: String?): String? {
    if (url.isNullOrBlank()) return null
    val uri = try {
        URI(url)
    } catch (e: URISyntaxException) {
        return null
    }
    val scheme = uri.scheme?.lowercase(Locale.ROOT)?.takeIf { it == HTTP || it == HTTPS } ?: return null
    val host = uri.host?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() } ?: return null
    val port = uri.port.takeIf { it != -1 && it != defaultPort(scheme) }?.let { ":$it" }.orEmpty()
    return "$scheme://$host$port"
}

/** [url]이 [origin]과 같은 origin인가. */
internal fun isSameOrigin(url: String?, origin: String): Boolean = webOriginOf(url) == webOriginOf(origin)

/** 상세 주소에 현재 앱 테마를 붙인다(이벤트 계약 4절): "?theme=light" 또는 "?theme=dark". 이미 질의가 있으면 "&"로 잇는다. */
internal fun withTheme(url: String, isDark: Boolean): String {
    val fragmentAt = url.indexOf('#').takeIf { it >= 0 } ?: url.length
    val base = url.substring(0, fragmentAt)
    val separator = if (base.contains('?')) "&" else "?"
    return "$base${separator}theme=${if (isDark) "dark" else "light"}${url.substring(fragmentAt)}"
}

/** 바깥 브라우저로 넘겨도 되는 주소인가(http·https만, intent: 등 다른 스킴은 열지 않는다). */
internal fun isExternalWebLink(url: String?): Boolean =
    url?.substringBefore(':')?.lowercase(Locale.ROOT).let { it == HTTP || it == HTTPS }

private fun defaultPort(scheme: String): Int = if (scheme == HTTPS) HTTPS_PORT else HTTP_PORT

private const val HTTP = "http"
private const val HTTPS = "https"
private const val HTTP_PORT = 80
private const val HTTPS_PORT = 443
