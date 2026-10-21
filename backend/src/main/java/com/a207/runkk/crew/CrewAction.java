package com.a207.runkk.crew;

/** 크루 권한표 12행(srs.md 3.0.3). CrewPermissionPolicy가 관계별 허용을 정한다. */
public enum CrewAction {
    VIEW_PUBLIC_INFO,
    EDIT_CREW,
    TRANSFER_OR_DELETE,
    VIEW_OR_DECIDE_JOIN,
    KICK,
    WRITE_POST,
    VOTE_OR_JOIN_CREW_RUN,
    DELETE_ANY_POST,
    DELETE_OWN_POST,
    VIEW_CREW_INTERNAL,
    EDIT_OWN_CREW_PROFILE,
    LEAVE
}
