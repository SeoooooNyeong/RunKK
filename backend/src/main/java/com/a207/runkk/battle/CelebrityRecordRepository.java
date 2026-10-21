package com.a207.runkk.battle;

import com.a207.runkk.common.StandardDistance;
import java.util.List;
import java.util.Optional;

/** 유명인 기록 저장소 포트(설계 6.1.6). 구현은 infra.battle.JpaCelebrityRecordRepository. */
public interface CelebrityRecordRepository {

    /** 거리 필터가 있으면 그 기준 거리의 기록만, 등록 최신순. */
    List<CelebrityRecord> findAll(Optional<StandardDistance> filter);

    Optional<CelebrityRecord> findById(long id);

    CelebrityRecord save(CelebrityRecord record);

    void delete(long id);
}
