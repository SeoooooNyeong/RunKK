package com.a207.runkk.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.ReasonCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 설계 v0.11 member enum(F.5.1, 06-01 AccountType·ScreenTheme·SettingItem). */
@DisplayName("v0.11 member enum")
class MemberEnumsV011Test {

    @Test
    @DisplayName("계정 유형은 GUEST·LINKED, 화면 테마는 SYSTEM·LIGHT·DARK다")
    void accountTypeAndTheme() {
        assertThat(AccountType.values()).containsExactly(AccountType.GUEST, AccountType.LINKED);
        assertThat(ScreenTheme.values())
                .containsExactly(ScreenTheme.SYSTEM, ScreenTheme.LIGHT, ScreenTheme.DARK);
    }

    @Test
    @DisplayName("설정 항목은 14개이고 THEME은 ScreenTheme 이름만 받는 설정(동의 아님)이다")
    void themeSetting() {
        assertThat(SettingItem.values()).hasSize(14).endsWith(SettingItem.THEME);
        assertThat(SettingItem.THEME.consentItem()).isEmpty();
        SettingItem.THEME.validate("DARK");
        SettingItem.THEME.validate("SYSTEM");
        assertThatThrownBy(() -> SettingItem.THEME.validate("BLUE"))
                .isInstanceOfSatisfying(
                        BusinessRuleException.class,
                        e -> assertThat(e.reason()).isEqualTo(ReasonCode.SETTING_INVALID));
        assertThatThrownBy(() -> SettingItem.THEME.validate(null))
                .isInstanceOf(BusinessRuleException.class);
    }
}
