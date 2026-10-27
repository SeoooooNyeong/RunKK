package com.a207.runkk.crew;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.ReasonCode;
import org.springframework.stereotype.Component;

/**
 * 크루를 읽고 권한표로 요청을 검사한다(CrewRepository + CrewPermissionPolicy). 응용 서비스마다 "크루 읽기 → 관계 → check"를 되풀이하지
 * 않고, 서비스 생성자 의존을 Fan-Out 7 안에 둔다. 자료를 바꾸기 전에 부른다(FR-128).
 */
@Component
class CrewAccess {

    private final CrewRepository crewRepository;
    private final CrewPermissionPolicy permissionPolicy;

    CrewAccess(CrewRepository crewRepository, CrewPermissionPolicy permissionPolicy) {
        this.crewRepository = crewRepository;
        this.permissionPolicy = permissionPolicy;
    }

    /** 없으면 CREW_NOT_FOUND. */
    Crew require(long crewId) {
        return crewRepository
                .findById(crewId)
                .orElseThrow(() -> new BusinessRuleException(ReasonCode.CREW_NOT_FOUND));
    }

    /** 크루 행을 잠가 읽는다. 없으면 CREW_NOT_FOUND. */
    Crew requireForUpdate(long crewId) {
        return crewRepository
                .findByIdForUpdate(crewId)
                .orElseThrow(() -> new BusinessRuleException(ReasonCode.CREW_NOT_FOUND));
    }

    /** 읽고 열람자 관계로 검사한다. 허용이 아니면 PermissionDeniedException. */
    Crew check(long memberId, long crewId, CrewAction action) {
        Crew crew = require(crewId);
        permissionPolicy.check(crew.relationOf(memberId), action);
        return crew;
    }

    /** 화면 버튼 표시용: 그 회원이 이 크루에서 그 행동을 할 수 있는가(예외 없음). */
    boolean allows(Crew crew, long memberId, CrewAction action) {
        return permissionPolicy.isAllowed(crew.relationOf(memberId), action);
    }

    /** 잠가 읽고 검사한다. */
    Crew checkForUpdate(long memberId, long crewId, CrewAction action) {
        Crew crew = requireForUpdate(crewId);
        permissionPolicy.check(crew.relationOf(memberId), action);
        return crew;
    }
}
