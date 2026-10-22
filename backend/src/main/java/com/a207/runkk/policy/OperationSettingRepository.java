package com.a207.runkk.policy;

import java.util.List;
import java.util.Optional;

/** 운영 기준값 저장 포트(설계 6.1.9). 구현 infra.policy.JpaOperationSettingRepository. policy 밖에서 쓰지 않는다. */
public interface OperationSettingRepository {

    Optional<OperationSetting> find(SettingKey key);

    OperationSetting save(OperationSetting setting);

    void saveHistory(OperationSettingHistory history);

    List<OperationSetting> findAll();
}
