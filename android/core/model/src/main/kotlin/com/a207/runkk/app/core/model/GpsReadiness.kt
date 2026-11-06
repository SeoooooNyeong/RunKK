package com.a207.runkk.app.core.model

/** 측정 전 GPS 예열 상태(W55). 찾는 중이거나, 측정 기준 정확도 안의 위치를 받아 준비됐다. */
enum class GpsReadiness {
    SEARCHING,
    READY
}
