package com.a207.runkk.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.ReasonCode;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.LocalDateTime;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class OperationSettingTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 20, 15, 0);

    @Test
    void initial_holdsDefaultValue() {
        OperationSetting setting = OperationSetting.initial(SettingKey.SELFIE_LIMIT, NOW);
        assertThat(setting.key()).isEqualTo(SettingKey.SELFIE_LIMIT);
        assertThat(setting.value()).isEqualTo(SettingKey.SELFIE_LIMIT.defaultValue());
    }

    @Test
    void change_returnsHistoryWithBeforeAndAfter() {
        OperationSetting setting = OperationSetting.initial(SettingKey.SELFIE_LIMIT, NOW);
        String before = setting.value();

        OperationSettingHistory history =
                setting.change("{\"perDay\":3}", "KAKAO:123", NOW.plusMinutes(1));

        assertThat(setting.value()).isEqualTo("{\"perDay\":3}");
        assertThat(history.key()).isEqualTo(SettingKey.SELFIE_LIMIT);
        assertThat(history.before()).isEqualTo(before);
        assertThat(history.after()).isEqualTo("{\"perDay\":3}");
        assertThat(history.changedAt()).isEqualTo(NOW.plusMinutes(1));
        assertThat(history.operatorId()).isEqualTo("KAKAO:123");
    }

    @Test
    void change_rejectsInvalidValueWithValueInvalid() {
        OperationSetting setting = OperationSetting.initial(SettingKey.SELFIE_LIMIT, NOW);
        String before = setting.value();

        assertThatThrownBy(() -> setting.change("{\"perDay\":0}", "KAKAO:123", NOW))
                .isInstanceOfSatisfying(
                        BusinessRuleException.class,
                        e -> assertThat(e.reason()).isEqualTo(ReasonCode.VALUE_INVALID));
        assertThat(setting.value()).isEqualTo(before);
    }

    @Test
    void change_rejectsForbiddenTransition() {
        OperationSetting setting = OperationSetting.initial(SettingKey.SHOP_ITEMS, NOW);
        setting.change(
                "{\"items\":[{\"id\":1,\"name\":\"모자\",\"slot\":\"HAT\",\"price\":100,\"onSale\":true}]}",
                "KAKAO:1",
                NOW);

        assertThatThrownBy(() -> setting.change("{\"items\":[]}", "KAKAO:1", NOW))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void change_requiresActor() {
        OperationSetting setting = OperationSetting.initial(SettingKey.SELFIE_LIMIT, NOW);
        assertThatThrownBy(() -> setting.change("{\"perDay\":3}", " ", NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void entity_hasNoPublicSetters() {
        assertThat(Arrays.stream(OperationSetting.class.getDeclaredMethods()))
                .filteredOn(m -> Modifier.isPublic(m.getModifiers()))
                .extracting(Method::getName)
                .noneMatch(name -> name.startsWith("set"));
    }
}
