package com.a207.runkk.app.domain.crew

import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Gender
import com.a207.runkk.app.core.model.Outcome
import java.time.Instant
import java.time.LocalDate

/**
 * 크루원 목록(서버 crew.CrewMembersView + Runimal 유형, D-46·D-52). 비공개 크루원은 표시명·역할·소개만 온다.
 * [Member.isMe]는 열람자 본인 줄이다. role은 서버 역할 이름을 CrewRole로 바꾼 값이다(모르는 이름은 MEMBER).
 * 피그마 맞춤 W7(contract-W7 2장): 서버가 누적 거리 내림차순으로 준다. 나이대·성별은 공개한 값만(본인은 모두) 오고,
 * 공개 설정 두 값([Member.isAgePublic]·[Member.isGenderPublic])은 본인 줄에만 있다.
 */
data class CrewMembers(val members: List<Member>) {
    data class Member(
        val memberId: Long,
        val displayName: String,
        val role: CrewRole,
        val crewIntro: String?,
        val tier: String?,
        val runimalType: String?,
        val isMe: Boolean = false,
        /** 이 크루에 반영된 누적 거리(F-22 목록 오른쪽). */
        val accumulated: Distance = Distance(0),
        /** 크루 가입 일시(F-22b "가입일"). 옛 서버면 null. */
        val joinedAt: Instant? = null,
        val level: Int? = null,
        /** 나이대("20대"). 비공개·미입력이면 null. */
        val ageBand: String? = null,
        /** 성별. 비공개·미입력이면 null. */
        val gender: Gender? = null,
        /** 본인 줄만: 나이대 공개. 남의 줄은 null. */
        val isAgePublic: Boolean? = null,
        /** 본인 줄만: 성별 공개. 남의 줄은 null. */
        val isGenderPublic: Boolean? = null,
        /** 아바타 얼굴 키. 서버가 공개 판정을 해서 준다(프로필을 볼 수 없으면 null). 옛 서버·얼굴 없음도 null. */
        val faceKey: String? = null,
        /** 표시 성별([faceKey]가 없을 때 그릴 기본 얼굴). 옛 서버면 null. */
        val shownGender: Gender? = null
    )
}
