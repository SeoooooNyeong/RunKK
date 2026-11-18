package com.a207.runkk.app.api.account

import com.a207.runkk.app.api.core.ApiErrorMapper
import com.a207.runkk.app.api.core.SelfMember
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.account.PlacementProgress
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 배치고사 진행 기기 사본(Jira 325). 홈은 복귀마다 GET /me/placement를 부르지 않고 이 사본을 읽는다.
 *
 * - 사본은 서버에서 읽을 때마다([refresh], AccountRepository.placement) 바뀐다: 앱 시작 뒤 첫 홈 읽기, 배치고사 판 결과 뒤 진행 화면의
 *   읽기, 배틀런 카드 시트 열기, 홈 다시 시도.
 * - 읽은 회원의 것이다. 로그인한 회원이 다르거나 로그아웃이면 내주지 않는다.
 * - 대결 결과를 읽으면(판 저장 뒤) [invalidate]로 비워 다음 홈 읽기가 서버에서 다시 받는다.
 * 프로세스 메모리에만 둔다(앱을 새로 켜면 첫 홈 읽기가 서버에서 한 번 받는다). 여러 스레드가 함께 써도 된다.
 */
@Singleton
internal class PlacementCache internal constructor(
    private val fetch: suspend () -> Outcome<PlacementProgress>,
    private val member: () -> Long?
) {
    @Inject
    constructor(me: MeApi, self: SelfMember) : this(
        fetch = { ApiErrorMapper.call { me.placement().toDomain() } },
        member = self::memberId
    )

    private class Held(val member: Long, val progress: PlacementProgress)

    @Volatile
    private var held: Held? = null

    /** 로그인한 회원의 사본. 없거나 다른 회원 것이면 null. */
    fun read(): PlacementProgress? {
        val current = member() ?: return null
        return held?.takeIf { it.member == current }?.progress
    }

    /** 서버에서 읽어 사본을 바꾼다. 실패하면 사본을 그대로 둔다. 읽는 사이 회원이 바뀌었으면 담지 않는다. */
    suspend fun refresh(): Outcome<PlacementProgress> {
        val asked = member()
        val result = fetch()
        if (result is Outcome.Success && asked != null && asked == member()) held = Held(asked, result.value)
        return result
    }

    fun invalidate() {
        held = null
    }
}
