package com.a207.runkk.event;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.ReasonCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;

/**
 * 이벤트 종류별 설정(event.config JSON, S15P31A207-275). COACH_CELEB는
 * {"celebrityIds":[11,12],"pointPerWin":500}, RUN_DISTANCE는 {"minDistanceMeters":1000}이다.
 *
 * @param celebrityIds 이겨야 하는 유명인 기록 ID(COACH_CELEB)
 * @param pointPerWin 셀럽마다 처음 이겼을 때 주는 포인트(COACH_CELEB, 0이면 주지 않음)
 * @param minDistanceMeters 응모 기준 거리(RUN_DISTANCE)
 */
record EventConfig(List<Long> celebrityIds, Integer pointPerWin, Long minDistanceMeters) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 종류에 맞는 값이 없거나 범위 밖이면 VALUE_INVALID(409). */
    static EventConfig parse(EventKind kind, String json) {
        EventConfig config;
        try {
            config = MAPPER.readValue(json, EventConfig.class);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new BusinessRuleException(ReasonCode.VALUE_INVALID);
        }
        if (config == null) {
            // JSON "null"은 예외 없이 null로 읽힌다
            throw new BusinessRuleException(ReasonCode.VALUE_INVALID);
        }
        boolean valid =
                switch (kind) {
                    case COACH_CELEB ->
                            config.celebrityIds() != null
                                    && !config.celebrityIds().isEmpty()
                                    && config.celebrityIds().stream()
                                            .allMatch(id -> id != null && id > 0)
                                    && config.celebrityIds().stream().distinct().count()
                                            == config.celebrityIds().size()
                                    && config.pointPerWin() != null
                                    && config.pointPerWin() >= 0;
                    case RUN_DISTANCE ->
                            config.minDistanceMeters() != null && config.minDistanceMeters() > 0;
                };
        if (!valid) {
            throw new BusinessRuleException(ReasonCode.VALUE_INVALID);
        }
        return config;
    }
}
