package com.a207.runkk.member;

import java.util.Optional;

/**
 * 동의가 아닌 설정 변경 요청 하나(설계 6.1.2). 알림 종류는 notification.NotificationType의 이름이다(member → notification
 * 의존을 만들지 않으려고 이름 문자열로 받는다, B2 결정).
 *
 * @param item 설정 항목
 * @param value 설정 값
 * @param notificationType 알림 종류 이름, ALERT 항목일 때만
 */
public record SettingChange(SettingItem item, String value, String notificationType) {

    public SettingChange {
        if (item == null) {
            throw new IllegalArgumentException("설정 항목이 비었다");
        }
        if (item == SettingItem.ALERT && (notificationType == null || notificationType.isBlank())) {
            throw new IllegalArgumentException("알림수신 설정에는 알림 종류가 필요하다");
        }
    }

    public boolean isConsentChange() {
        return item.consentItem().isPresent();
    }

    public Optional<ConsentItem> consentItem() {
        return item.consentItem();
    }
}
