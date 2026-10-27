package com.a207.runkk.crew;

import com.a207.runkk.common.PermissionDeniedException;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 크루 권한표 하나로 요청을 검사한다(설계 6.1.7, srs.md 3.0.3 권한표(운영진 열 포함), FR-128, D-98). 표는 16행을 그대로 옮긴 불변 표다.
 * 자료를 바꾸기 전에 부른다.
 */
@Component
public class CrewPermissionPolicy {

    private static final Set<CrewRelation> LEADER_ONLY = EnumSet.of(CrewRelation.LEADER);

    /** 크루장·운영진(L·M). */
    private static final Set<CrewRelation> STAFF =
            EnumSet.of(CrewRelation.LEADER, CrewRelation.MANAGER);

    /** 크루 구성원(L·M·R). */
    private static final Set<CrewRelation> MEMBERS =
            EnumSet.of(CrewRelation.LEADER, CrewRelation.MANAGER, CrewRelation.MEMBER);

    private final Map<CrewAction, Set<CrewRelation>> table;

    public CrewPermissionPolicy() {
        Map<CrewAction, Set<CrewRelation>> t = new EnumMap<>(CrewAction.class);
        t.put(CrewAction.VIEW_PUBLIC_INFO, EnumSet.allOf(CrewRelation.class));
        t.put(CrewAction.EDIT_CREW, LEADER_ONLY);
        t.put(CrewAction.TRANSFER_OR_DELETE, LEADER_ONLY);
        t.put(CrewAction.APPOINT_MANAGER, LEADER_ONLY);
        t.put(CrewAction.KICK, LEADER_ONLY);
        t.put(CrewAction.VIEW_OR_DECIDE_JOIN, STAFF);
        t.put(CrewAction.MANAGE_NOTICE, STAFF);
        t.put(CrewAction.SET_GOAL, STAFF);
        t.put(CrewAction.WRITE_POST, MEMBERS);
        t.put(CrewAction.RESPOND, MEMBERS);
        t.put(CrewAction.REPORT, MEMBERS);
        t.put(CrewAction.DELETE_ANY_CONTENT, STAFF);
        t.put(CrewAction.DELETE_OWN_CONTENT, MEMBERS);
        t.put(CrewAction.VIEW_CREW_INTERNAL, MEMBERS);
        t.put(CrewAction.EDIT_OWN_CREW_PROFILE, MEMBERS);
        t.put(CrewAction.LEAVE, MEMBERS);
        t.replaceAll((action, relations) -> Collections.unmodifiableSet(EnumSet.copyOf(relations)));
        this.table = Collections.unmodifiableMap(t);
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

    /** 표 자체(불변, 시험·점검용). */
    Map<CrewAction, Set<CrewRelation>> table() {
        return table;
    }
}
