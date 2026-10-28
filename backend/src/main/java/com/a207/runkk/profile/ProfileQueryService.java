package com.a207.runkk.profile;

import com.a207.runkk.avatar.AvatarQuery;
import com.a207.runkk.crew.CrewMembershipReader;
import com.a207.runkk.member.DisclosureLevel;
import com.a207.runkk.member.MemberQueryService;
import com.a207.runkk.member.MemberSummary;
import com.a207.runkk.runimal.RunimalQuery;
import com.a207.runkk.runimal.RunimalType;
import com.a207.runkk.running.RunQueryService;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 프로필 조회를 조정한다(설계 6.1.5 ProfileQueryService, D-29, D-52). 공개 수준은
 * member.MemberQueryService.disclosureOf가 정하고 이 서비스는 ProfileDisclosure로 항목만 거른다. 다른 패키지의
 * 엔티티·Repository를 받지 않고 조회 연산만 부른다(D-32).
 */
@Service
@Transactional(readOnly = true)
public class ProfileQueryService {

    private final MemberQueryService memberQueryService;
    private final RunimalQuery runimalQuery;
    private final AvatarQuery avatarQuery;
    private final CrewMembershipReader crewMembershipReader;
    private final RunQueryService runQueryService;
    private final Clock clock;

    public ProfileQueryService(
            MemberQueryService memberQueryService,
            RunimalQuery runimalQuery,
            AvatarQuery avatarQuery,
            CrewMembershipReader crewMembershipReader,
            RunQueryService runQueryService,
            Clock clock) {
        this.memberQueryService = memberQueryService;
        this.runimalQuery = runimalQuery;
        this.avatarQuery = avatarQuery;
        this.crewMembershipReader = crewMembershipReader;
        this.runQueryService = runQueryService;
        this.clock = clock;
    }

    /** 없는 회원이면 IllegalArgumentException(400). 탈퇴 회원은 다른 항목을 모으지 않는다. */
    public ProfileView view(long viewerId, long targetId) {
        DisclosureLevel level =
                memberQueryService.disclosureOf(viewerId, List.of(targetId)).get(targetId);
        if (level == null) {
            throw new IllegalArgumentException("회원 없음: " + targetId);
        }
        ProfileDisclosure disclosure = ProfileDisclosure.from(level);
        if (disclosure == ProfileDisclosure.WITHDRAWN) {
            return disclosure.filter(ProfileData.withdrawn(targetId));
        }
        return disclosure.filter(collect(targetId));
    }

    private ProfileData collect(long targetId) {
        MemberSummary summary = memberQueryService.summaries(List.of(targetId)).get(targetId);
        Map<Long, RunimalType> types = runimalQuery.typesOf(List.of(targetId));
        RunimalType type = types.get(targetId);
        return new ProfileData(
                targetId,
                summary.displayName(),
                avatarQuery.faceKey(targetId).orElse(null),
                avatarQuery.backgroundKeyOf(targetId).orElse(null),
                summary.tier(),
                type == null ? null : type.name(),
                crewMembershipReader
                        .activeCrewOf(targetId)
                        .map(c -> new ProfileView.Crew(c.crewId(), c.name(), c.logoUrl()))
                        .orElse(null),
                runQueryService.longestDistance(targetId).orElse(null),
                memberQueryService.preferenceOf(targetId),
                memberQueryService.profileOf(targetId, LocalDate.now(clock).getYear()));
    }
}
