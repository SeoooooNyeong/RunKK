package com.a207.runkk.crew;

import com.a207.runkk.common.DisplayName;
import com.a207.runkk.common.TagSet;
import com.a207.runkk.common.Tier;
import java.util.List;
import java.util.Set;

/**
 * 크루원 목록(자료사전 크루원목록, FR-135). 공개 수준이 showsProfile()이 아닌 크루원은 표시명·역할·크루 내 소개만 담는다(D-52). Runimal 유형은
 * web.crew의 CrewMembersAssembler가 disclosedMemberIds로 붙인다(D-46).
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
     * @param crewTags 크루 내 성향 태그, 비공개면 null
     * @param tier 티어, 비공개이거나 언랭이면 null
     */
    public record Line(
            long memberId,
            DisplayName name,
            CrewRole role,
            String crewIntro,
            TagSet crewTags,
            Tier tier) {}
}
