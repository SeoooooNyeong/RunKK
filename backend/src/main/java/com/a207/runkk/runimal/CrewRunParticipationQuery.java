package com.a207.runkk.runimal;

import com.a207.runkk.common.DateRange;
import java.util.Set;

/**
 * 크루런 참가 기록 조회 포트(설계 6.1.8, D-46). runimal이 정의하고 crew.CrewRunParticipationReader가 구현한다(동행 축,
 * FR-146).
 */
public interface CrewRunParticipationQuery {

    /** 기간 안에 크루런에 연결된 회원의 기록 ID. */
    Set<String> linkedRecordIds(long memberId, DateRange range);
}
