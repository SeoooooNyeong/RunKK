package com.a207.runkk.app.domain.settings

import java.time.Duration
import java.time.Instant
import javax.inject.Inject

/**
 * 같은 자료를 너무 자주 서버에서 다시 읽지 않게 막는 문지기(Jira 325, 홈 복귀). 열쇠(예: "home", "events")마다 마지막 성공 시각과
 * 진행 중 여부를 가진다.
 *
 * - [shouldRun]: 진행 중이면 거짓(강제여도), 강제면 참, 마지막 성공에서 [minInterval]이 안 지났으면 거짓, 그 밖은 참. 참을 돌려주면
 *   그 열쇠를 진행 중으로 표시하므로 부른 쪽은 읽기를 마치면 반드시 [markDone]을 부른다.
 * - [markDone]: 진행 중 표시를 풀고, 성공([ok])이면 그 시각을 남긴다. 실패는 시각을 남기지 않아 다음 요청이 바로 통과한다.
 * 순수 클래스다. 주 스레드(ViewModel)에서만 부른다(잠금 없음).
 */
class RefreshGate(private val minInterval: Duration) {
    @Inject
    constructor() : this(DEFAULT_INTERVAL)

    private val lastSuccess = HashMap<String, Instant>()
    private val running = HashSet<String>()

    fun shouldRun(key: String, now: Instant, force: Boolean): Boolean {
        if (key in running) return false
        val last = lastSuccess[key]
        val isFresh = last != null && Duration.between(last, now) < minInterval
        if (!force && isFresh) return false
        running += key
        return true
    }

    fun markDone(key: String, now: Instant, ok: Boolean) {
        running -= key
        if (ok) lastSuccess[key] = now
    }

    companion object {
        /** 기본 최소 간격 30초(지휘자 결정 2026-11-18). */
        val DEFAULT_INTERVAL: Duration = Duration.ofSeconds(30)
    }
}
