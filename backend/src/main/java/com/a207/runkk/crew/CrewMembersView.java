package com.a207.runkk.crew;

import com.a207.runkk.common.DisplayName;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.Tier;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 크루원 목록(자료사전 크루원목록, FR-135). 누적 기여 거리 내림차순 → 가입 일시 → 회원 ID 순(피그마 맞춤 W7). 공개 수준이 showsProfile()이 아닌
 * 크루원은 표시명·역할·크루 내 소개만 담는다(D-52). Runimal 유형은 web.crew의 CrewMembersAssembler가 disclosedMemberIds로
 * 붙인다(D-46).
 *
 * @param crewId 크루 ID
 * @param members 소속 중 크루원
 * @param disclosedMemberIds 프로필을 보일 크루원 ID
 */
public record CrewMembersView(long crewId, List<Line> members, Set<Long> disclosedMemberIds) {

    public CrewMembersView {
        members = List.copyOf(members);
        disclosedMemberIds = Set.copyOf(disclosedMemberIds);
    }

    /**
     * 크루원 한 명.
     *
     * @param memberId 회원 ID
     * @param name 표시명
     * @param role 크루 역할
     * @param crewIntro 크루 내 소개
     * @param tier 티어, 비공개이거나 언랭이면 null
     * @param accumulated 이 크루에 반영된 누적 기여 거리(W7 결정 W7-1)
     * @param joinedAt 지금 소속의 크루 가입 일시
     * @param profile 레벨·나이대·성별(공개 설정 반영, CrewMemberProfile)
     * @param shownGender 다른 러너에게 보이는 표시 성별 이름(MALE | FEMALE, member.ShownGender). 얼굴 그림의 기본 캐릭터를
     *     고르는 값이라 성별 비공개여도 대체값이 늘 있다(설계 5.31 [임시:D-성-226])
     */
    public record Line(
            long memberId,
            DisplayName name,
            CrewRole role,
            String crewIntro,
            Tier tier,
            Distance accumulated,
            LocalDateTime joinedAt,
            CrewMemberProfile profile,
            String shownGender) {}
}
