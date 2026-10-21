package com.a207.runkk.battle;

import com.a207.runkk.common.DateRange;
import com.a207.runkk.common.Week;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** 대결 저장소 포트(설계 6.1.6). 구현은 infra.battle.JpaBattleRepository. battle 밖에서 쓰지 않는다(ArchUnit 규칙 4). */
public interface BattleRepository {

    Battle save(Battle battle);

    Optional<Battle> findById(long id);

    /** 회원이 참가한 활성 대결(WAITING_START, IN_PROGRESS, COUNTDOWN)이 있는가. */
    boolean existsActive(long memberId);

    /** startAt ≤ now인 WAITING_START 대결. */
    List<Battle> findStartDue(LocalDateTime now);

    /** 카운트다운이 끝났거나(countdownEndsAt ≤ now) 최대 시간이 지난 진행 중 대결(Battle.isCloseDue). */
    List<Battle> findCountdownDue(LocalDateTime now);

    /** 기록 상대 도착 시각이 지났는데 아직 기록하지 않은 진행 중 대결(Battle.isOpponentArrivalDue). */
    List<Battle> findOpponentArrivalDue(LocalDateTime now);

    /** 보류가 cutoff 이전에 시작된 RESULT_PENDING 대결(cutoff = now − 24시간). */
    List<Battle> findPendingExpired(LocalDateTime cutoff);

    /** 회원이 참가한 FINISHED·RESULT_PENDING 대결, 생성 일시 최신순(FR-101). */
    List<Battle> findHistory(long memberId, DateRange range);

    /** 이번 주 전적(FR-093 주간 전적). */
    WeeklyRecord weeklyRecord(long memberId, Week week);
}
