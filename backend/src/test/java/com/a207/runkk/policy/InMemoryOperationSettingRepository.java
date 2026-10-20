package com.a207.runkk.policy;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 시험용 인메모리 OperationSettingRepository(설계 13.1). */
public class InMemoryOperationSettingRepository implements OperationSettingRepository {

    private final Map<SettingKey, OperationSetting> settings = new EnumMap<>(SettingKey.class);
    private final List<OperationSettingHistory> histories = new ArrayList<>();
    private int findCalls;

    @Override
    public Optional<OperationSetting> find(SettingKey key) {
        findCalls++;
        return Optional.ofNullable(settings.get(key));
    }

    @Override
    public OperationSetting save(OperationSetting setting) {
        settings.put(setting.key(), setting);
        return setting;
    }

    @Override
    public void saveHistory(OperationSettingHistory history) {
        histories.add(history);
    }

    @Override
    public List<OperationSetting> findAll() {
        return List.copyOf(settings.values());
    }

    public List<OperationSettingHistory> histories() {
        return histories;
    }

    public int findCalls() {
        return findCalls;
    }
}
