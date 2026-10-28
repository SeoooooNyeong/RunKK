package com.a207.runkk.web.notification;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.a207.runkk.notification.NavigationTarget;
import com.a207.runkk.notification.NotificationRequest;
import com.a207.runkk.notification.NotificationService;
import com.a207.runkk.notification.NotificationType;
import com.a207.runkk.notification.Screen;
import com.a207.runkk.web.WebTestSupport;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/** GET /notifications, POST /notifications/{id}/open, PUT /me/push-token(설계 5.7.1, SQ-14). */
@DisplayName("web.notification 알림함·기기 토큰")
class NotificationControllerTest extends WebTestSupport {

    @Autowired NotificationService notifications;

    @Test
    @DisplayName("기기 토큰을 등록하고, 알림함은 최신순이며 열면 이동 대상을 준다. 남의 알림은 403")
    void inboxOpenAndPushToken() throws Exception {
        Session s = signUp();
        Session other = signUp();
        mvc.perform(
                        as(s, put("/api/v1/me/push-token"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(write(Map.of("token", "fcm-" + s.memberId()))))
                .andExpect(status().isNoContent());
        mvc.perform(
                        as(s, put("/api/v1/me/push-token"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(write(Map.of("token", " "))))
                .andExpect(status().isBadRequest());

        notifications.notify(
                new NotificationRequest(
                        s.memberId(),
                        NotificationType.JOIN_DECIDED,
                        "가입이 승인되었어요",
                        new NavigationTarget(Screen.CREW_HOME, "7")));
        notifications.notify(
                new NotificationRequest(s.memberId(), NotificationType.PLAN_MISSED, "오늘 계획", null));

        String inbox =
                mvc.perform(as(s, get("/api/v1/notifications")))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.items.length()").value(2))
                        .andExpect(jsonPath("$.items[0].read").value(false))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        long first = body(inbox).get("items").get(1).get("id").asLong();
        mvc.perform(as(s, post("/api/v1/notifications/" + first + "/open")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.screen").value("CREW_HOME"))
                .andExpect(jsonPath("$.targetId").value("7"));
        mvc.perform(as(other, post("/api/v1/notifications/" + first + "/open")))
                .andExpect(status().isForbidden());
        mvc.perform(as(s, post("/api/v1/notifications/9999999/open")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"));

        String today = LocalDate.now().toString();
        mvc.perform(as(s, get("/api/v1/notifications").param("from", today).param("to", today)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2));
        mvc.perform(
                        as(
                                s,
                                get("/api/v1/notifications")
                                        .param("from", "2000-01-01")
                                        .param("to", "2000-01-02")))
                .andExpect(jsonPath("$.items.length()").value(0));
        mvc.perform(as(s, get("/api/v1/notifications").param("from", "oops").param("to", today)))
                .andExpect(status().isBadRequest());
    }
}
