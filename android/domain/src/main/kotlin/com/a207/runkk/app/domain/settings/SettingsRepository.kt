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

    /** 서버 사본의 계정 유형이 GUEST인가(연동하지 않은 게스트, FR-199). 사본이 없으면 거짓이다. */
    fun isGuest(): Boolean

    /** 값이 바뀔 때마다 알린다. WatchLink가 동의 철회를 바로 반영한다. */
    fun changes(): Flow<SettingsSnapshot>

    /**
     * 첫 사용 가이드 기기 사본(D-277). 가이드 말고는 쓰는 곳이 없어 기본 구현(사본 없음, 저장은 무시)을 둔다.
     * 구현(data.settings)은 같은 기기 파일에 두고 [clear] 때 함께 지운다(다른 회원이 로그인하면 서버 값을 다시 읽는다).
     */
    suspend fun guideCopy(): GuideCopy = GuideCopy.NONE

    suspend fun saveGuideCopy(copy: GuideCopy): Outcome<Unit> = Outcome.Success(Unit)

    /** 사본 삭제(로그아웃·탈퇴·로그인 상태 해제). */
    suspend fun clear(): Outcome<Unit>
}
