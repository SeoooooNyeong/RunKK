package com.a207.runkk.app.core.avatar

import timber.log.Timber

/**
 * 홈 아바타 합성 구간 표식(S15P31A207-345 9회차). 측정 작업자가 로그에서 구간별 시간을 잴 수 있게 `AvatarPrepare <구간> <ms>[ 덧붙임]`을
 * Timber.d로 남긴다. 구간 이름: inputs(아바타·러너·상점 응답), head-fetch(내 머리 층 읽기), head-layers(머리 층 합성 또는 디스크 사본),
 * head-decode·head-variants(머리 층 합성 안), riv-read·riv-wait(Rive 파일 읽기·기다리기), tone-wait(합성 차례 기다리기),
 * tone-thread(합성 스레드 기다리기), tone-embedded(riv 내장 그림 풀기), tone-compose(톤·의상·몸 합성), tone-disk-read·tone-disk-write,
 * rive-hold(Rive 서기 파일 가져오기), total(로딩 화면의 홈 아바타 준비 전체).
 */
object AvatarPrepareTrace {
    /** 구간 시작 시각(ms, 단조 시계). */
    fun now(): Long = System.nanoTime() / NANOS_PER_MILLI

    /** [startedAt]([now])부터 지금까지를 [section] 구간으로 남기고 걸린 ms를 돌려준다. */
    fun mark(section: String, startedAt: Long, detail: String = ""): Long {
        val ms = now() - startedAt
        Timber.d("AvatarPrepare %s %d%s", section, ms, if (detail.isEmpty()) "" else " $detail")
        return ms
    }

    private const val NANOS_PER_MILLI = 1_000_000L
}
