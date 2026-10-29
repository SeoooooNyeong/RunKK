package com.a207.runkk.runimal;

import java.util.List;
import java.util.Map;

/** 유형 비율 저장소 포트(설계 5.6.2). 구현은 infra.runimal.JpaRunimalTypeShareRepository. runimal 밖에서 쓰지 않는다. */
public interface RunimalTypeShareRepository {

    /** 저장된 비율 행을 모두 바꾼다(16행). */
    void replaceAll(List<RunimalTypeShare> shares);

    /** 저장된 비율(아직 계산 전이면 빈 맵). */
    Map<RunimalType, RunimalTypeShare> findAll();

    /** 지금 확정 유형(runimal_profile.confirmed_type)별 러너 수. 확정자가 없는 유형은 맵에 없다. */
    Map<RunimalType, Integer> countConfirmedByType();
}
