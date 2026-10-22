package com.a207.runkk.notification;

/**
 * 알림을 눌렀을 때 열 화면(설계 6.1.10).
 *
 * @param screen 화면 종류
 * @param targetId 대상 ID(크루 ID, 대결 ID 등), 없을 수 있음
 */
public record NavigationTarget(Screen screen, String targetId) {

    public NavigationTarget {
        if (screen == null) {
            throw new IllegalArgumentException("화면 종류가 비었다");
        }
    }
}
