package com.a207.runkk.battle;

import com.a207.runkk.common.StandardDistance;
import com.a207.runkk.common.Tier;
import java.util.List;

/** 가상 기록 저장소 포트(설계 6.1.6, D16). 구현은 infra.battle.JpaVirtualRecordRepository. */
public interface VirtualRecordRepository {

    List<VirtualRecord> findByTierAndDistance(Tier tier, StandardDistance distance);

    long count();

    /** 기존 전부를 지우고 새 목록으로 바꾼다(한 트랜잭션, FR-104). */
    void replaceAll(List<VirtualRecord> records);

    boolean existsByNickname(String nickname);
}
