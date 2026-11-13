package com.a207.runkk.crew;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** 크루런 반복 저장소 포트(W6). 구현은 infra.crew.JpaCrewRunSeriesRepository. */
public interface CrewRunSeriesRepository {

    /** 새 반복이면 바로 INSERT까지 한다(회차 글에 ID를 붙여야 한다). */
    CrewRunSeries save(CrewRunSeries series);

    Optional<CrewRunSeries> findById(long id);

    /** 반복 행을 잠가(SELECT … FOR UPDATE) 읽는다. 회차 생성·수정·삭제가 같은 반복을 동시에 바꿀 때 줄을 세운다. */
    Optional<CrewRunSeries> findByIdForUpdate(long id);

    /**
     * 진행 중이고 최신 회차 일시 &lt; before인 반복 ID(부분 색인 idx_crew_run_series_due). 차례 판정은
     * CrewRunSeries.isDue가 다시 한다.
     */
    List<Long> findActiveIdsBefore(LocalDateTime before);
}
