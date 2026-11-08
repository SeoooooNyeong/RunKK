package com.a207.runkk.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 설계 v0.11 알림 종류·이동 화면(F.5.1, 06-05, D-150). TC-6201. */
@DisplayName("v0.11 알림 종류와 이동 화면")
class NotificationTypeV011Test {

    @Test
    @DisplayName("새 알림 종류 9개의 기본 화면")
    void newTypes_haveDefaultScreens() {
        Map<NotificationType, Screen> expected =
                Map.of(
                        NotificationType.CREW_NOTICE, Screen.CREW_NOTICE,
                        NotificationType.CREW_POLL, Screen.CREW_POLL,
                        NotificationType.POLL_CLOSING_SOON, Screen.CREW_POLL,
                        NotificationType.CREW_SCHEDULE, Screen.CREW_SCHEDULE,
                        NotificationType.CREW_CHAT, Screen.CREW_CHAT,
                        NotificationType.CREW_GOAL_ACHIEVED, Screen.CREW_HOME,
                        NotificationType.MANAGER_CHANGED, Screen.CREW_HOME,
                        NotificationType.BACKGROUND_UNLOCKED, Screen.AVATAR_BACKGROUND,
                        NotificationType.REPORT_RESOLVED, Screen.CREW_HOME);
        expected.forEach((type, screen) -> assertThat(type.defaultScreen()).isEqualTo(screen));
        // + 운영자 공지 NOTICE(S15P31A207-275) = 22
        assertThat(NotificationType.values()).hasSize(22);
    }

    @Test
    @DisplayName("이동 화면은 11개이고 CREW_BOARD는 없다(D-150)")
    void screens_areElevenWithoutCrewBoard() {
        // + 운영자 공지 링크 EXTERNAL_LINK(S15P31A207-275) = 12
        assertThat(Screen.values()).hasSize(12);
        assertThat(Arrays.stream(Screen.values()).map(Enum::name)).doesNotContain("CREW_BOARD");
    }

    @Test
    @DisplayName("알림 종류 이름은 notification.type 칼럼(30자)에 들어간다")
    void typeNames_fitColumn() {
        assertThat(NotificationType.values()).allMatch(t -> t.name().length() <= 30);
    }
}
