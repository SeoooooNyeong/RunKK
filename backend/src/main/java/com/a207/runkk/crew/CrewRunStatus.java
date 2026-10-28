package com.a207.runkk.crew;

/** 크루런 상태(상태도 2.4.5). 삭제는 CrewPost.deleted 플래그다. */
public enum CrewRunStatus {
    VOTING,
    LOBBY_OPEN,
    RUNNING,
    FINISHED,
    CANCELLED
}
