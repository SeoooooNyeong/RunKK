package com.a207.runkk.member;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.DistanceUnit;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.common.RecordVisibility;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.util.Optional;

/**
 * 설정 항목 13개(자료사전 설정항목, 설계 6.1.2). 상수마다 값 검사(validate)와 대응하는 동의 항목(consentItem)을 가진다. 값은 문자열이고
 * 러닝설정·대결설정은 각 record의 JSON이다. 설정에 반영하는 일(applyTo)도 상수별 메소드로 두어 MemberSettings에 항목 분기를 두지 않는다(6.4).
 */
public enum SettingItem {
    /** 알림수신. 알림 종류 하나의 수신 여부(SettingChange.notificationType). */
    ALERT {
        @Override
        Object parse(String value) {
            return parseBoolean(value);
        }

        @Override
        void applyTo(MemberSettings settings, SettingChange change) {
            settings.changeNotificationPref(
                    change.notificationType(), (Boolean) parse(change.value()));
        }
    },
    UNIT {
        @Override
        Object parse(String value) {
            return parseEnum(DistanceUnit.class, value);
        }

        @Override
        void applyTo(MemberSettings settings, SettingChange change) {
            settings.changeUnit((DistanceUnit) parse(change.value()));
        }
    },
    RECORD_VISIBILITY {
        @Override
        Object parse(String value) {
            return parseEnum(RecordVisibility.class, value);
        }

        @Override
        void applyTo(MemberSettings settings, SettingChange change) {
            settings.changeRecordVisibility((RecordVisibility) parse(change.value()));
        }
    },
    COURSE_MAP_SHARED {
        @Override
        Object parse(String value) {
            return parseBoolean(value);
        }

        @Override
        void applyTo(MemberSettings settings, SettingChange change) {
            settings.changeCourseMapShared((Boolean) parse(change.value()));
        }
    },
    PROFILE_VISIBILITY {
        @Override
        Object parse(String value) {
            return parseEnum(ProfileVisibility.class, value);
        }

        @Override
        void applyTo(MemberSettings settings, SettingChange change) {
            settings.changeProfileVisibility((ProfileVisibility) parse(change.value()));
        }
    },
    LOCATION_CONSENT(ConsentItem.LOCATION),
    MARKETING_CONSENT(ConsentItem.MARKETING),
    HEART_RATE_CONSENT(ConsentItem.HEART_RATE),
    LLM_TRANSFER_CONSENT(ConsentItem.LLM_TRANSFER),
    FACE_TRANSFER_CONSENT(ConsentItem.FACE_TRANSFER),
    WATCH_LINK {
        @Override
        Object parse(String value) {
            return parseBoolean(value);
        }

        @Override
        void applyTo(MemberSettings settings, SettingChange change) {
            settings.changeWatchLinked((Boolean) parse(change.value()));
        }
    },
    RUN_SETTINGS {
        @Override
        Object parse(String value) {
            return parseJson(RunSettings.class, value);
        }

        @Override
        void applyTo(MemberSettings settings, SettingChange change) {
            settings.changeRunSettings((RunSettings) parse(change.value()));
        }
    },
    BATTLE_SETTINGS {
        @Override
        Object parse(String value) {
            return parseJson(BattleSettings.class, value);
        }

        @Override
        void applyTo(MemberSettings settings, SettingChange change) {
            settings.changeBattleSettings((BattleSettings) parse(change.value()));
        }
    };

    private static final ObjectMapper JSON = JsonMapper.builder().build();

    private final ConsentItem consentItem;

    SettingItem() {
        this(null);
    }

    SettingItem(ConsentItem consentItem) {
        this.consentItem = consentItem;
    }

    /** 허용 값이 아니면 BusinessRuleException(SETTING_INVALID). */
    public void validate(String value) {
        parse(value);
    }

    /** 이 항목이 동의 항목이면 그 동의. 동의 항목은 동의 주기·철회로만 바꾼다(D-36). */
    public Optional<ConsentItem> consentItem() {
        return Optional.ofNullable(consentItem);
    }

    /** 동의 항목의 값 형식은 true/false다. 동의 항목은 설정으로 반영하지 않는다. */
    Object parse(String value) {
        return parseBoolean(value);
    }

    void applyTo(MemberSettings settings, SettingChange change) {
        throw new BusinessRuleException(ReasonCode.CONSENT_ITEM_NOT_SETTING);
    }

    private static Boolean parseBoolean(String value) {
        if ("true".equals(value)) {
            return Boolean.TRUE;
        }
        if ("false".equals(value)) {
            return Boolean.FALSE;
        }
        throw invalid();
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value) {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw invalid();
        }
    }

    private static <T> T parseJson(Class<T> type, String value) {
        if (value == null) {
            throw invalid();
        }
        try {
            return JSON.readValue(value, type);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw invalid();
        }
    }

    private static BusinessRuleException invalid() {
        return new BusinessRuleException(ReasonCode.SETTING_INVALID);
    }
}
