package com.a207.runkk.crew;

import com.a207.runkk.common.PermissionDeniedException;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 크루 권한표 하나로 요청을 검사한다(설계 6.1.7, srs.md 3.0.3, FR-128). 표는 권한표 12행을 그대로 옮긴 불변 표다. 자료를 바꾸기 전에 부른다.
 */
@Component
public class CrewPermissionPolicy {

    private static final Set<CrewRelation> LEADER_ONLY = EnumSet.of(CrewRelation.LEADER);
    private static final Set<CrewRelation> MEMBERS =
            EnumSet.of(CrewRelation.LEADER, CrewRelation.MEMBER);

    private final Map<CrewAction, Set<CrewRelation>> table = new EnumMap<>(CrewAction.class);

    public CrewPermissionPolicy() {
        table.put(CrewAction.VIEW_PUBLIC_INFO, EnumSet.allOf(CrewRelation.class));
        table.put(CrewAction.EDIT_CREW, LEADER_ONLY);
        table.put(CrewAction.TRANSFER_OR_DELETE, LEADER_ONLY);
        table.put(CrewAction.VIEW_OR_DECIDE_JOIN, LEADER_ONLY);
        table.put(CrewAction.KICK, LEADER_ONLY);
        table.put(CrewAction.WRITE_POST, MEMBERS);
        table.put(CrewAction.VOTE_OR_JOIN_CREW_RUN, MEMBERS);
        table.put(CrewAction.DELETE_ANY_POST, LEADER_ONLY);
        table.put(CrewAction.DELETE_OWN_POST, MEMBERS);
        table.put(CrewAction.VIEW_CREW_INTERNAL, MEMBERS);
        table.put(CrewAction.EDIT_OWN_CREW_PROFILE, MEMBERS);
        table.put(CrewAction.LEAVE, MEMBERS);
    }

    /** 허용이 아니면 PermissionDeniedException("권한 없음"). */
    public void check(CrewRelation relation, CrewAction action) {
        if (!isAllowed(relation, action)) {
            throw new PermissionDeniedException("권한 없음");
        }
    }

    /** 화면 버튼 표시용 조회. */
    public boolean isAllowed(CrewRelation relation, CrewAction action) {
        return table.get(action).contains(relation);
    }
}
