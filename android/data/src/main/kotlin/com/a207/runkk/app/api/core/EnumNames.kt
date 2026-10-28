package com.a207.runkk.app.api.core

/**
 * 서버 enum 이름 → 앱 enum. 앱이 모르는 이름(서버가 먼저 상수를 더한 경우)은 null이라 매퍼가 그 항목을 건너뛰거나 기본값을 쓴다.
 * api.*의 DTO 매퍼가 함께 쓴다.
 */
internal inline fun <reified T : Enum<T>> enumByName(name: String?): T? =
    enumValues<T>().firstOrNull { it.name == name }
