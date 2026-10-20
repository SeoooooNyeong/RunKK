package com.a207.runkk.app.domain.settings

import com.a207.runkk.app.core.model.Outcome
import kotlinx.coroutines.flow.Flow

/**
 * 서버 설정 사본 포트(D-53). 구현은 data.settings.DataStoreSettingsRepository 하나뿐이다.
 * 읽기 연산은 사본이 없으면 개인정보 쪽으로 안전한 값(SettingsSnapshot.SAFE)을 돌려준다.
 */
interface SettingsRepository {
    /** SettingsRemote.fetch → 사본 저장 → changes 방출. 실패하면 이전 사본을 유지한다. */
    suspend fun refresh(): Outcome<Unit>

    fun locationConsented(): Boolean

    fun heartRateConsented(): Boolean

    fun watchLinked(): Boolean

    fun courseMapShared(): Boolean

    fun birthYear(): Int?

    fun guidanceSettings(): GuidanceSettings

    /** 값이 바뀔 때마다 알린다. WatchLink가 동의 철회를 바로 반영한다. */
    fun changes(): Flow<SettingsSnapshot>

    /** 사본 삭제(로그아웃·탈퇴·로그인 상태 해제). */
    suspend fun clear(): Outcome<Unit>
}
