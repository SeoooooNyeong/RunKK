package com.a207.runkk.web.notification;

import com.a207.runkk.common.DateRange;
import com.a207.runkk.notification.InboxView;
import com.a207.runkk.notification.NavigationTarget;
import com.a207.runkk.notification.NotificationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 알림함과 기기 토큰 등록(설계 5.7.1: GET /notifications, POST /notifications/{id}/open, PUT /me/push-token →
 * NotificationService). PUT /me/push-token은 경로가 /me 아래지만 notification 자료라 이 컨트롤러가 받는다(D-43).
 */
@RestController
@RequestMapping("/api/v1")
public class NotificationController {

    private static final String MEMBER_ID = "memberId";
    private static final int TOKEN_MAX = 512;

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /** from·to(포함, yyyy-MM-dd)를 둘 다 주면 그 기간만, 아니면 전부. 최신순. */
    @GetMapping("/notifications")
    InboxView inbox(
            @RequestAttribute(MEMBER_ID) long memberId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate to) {
        Optional<DateRange> range =
                from != null && to != null
                        ? Optional.of(new DateRange(from, to))
                        : Optional.empty();
        return notificationService.inbox(memberId, range);
    }

    @PostMapping("/notifications/{id}/open")
    NavigationTarget open(@RequestAttribute(MEMBER_ID) long memberId, @PathVariable long id) {
        return notificationService.open(memberId, id);
    }

    @PutMapping("/me/push-token")
    ResponseEntity<Void> registerDevice(
            @RequestAttribute(MEMBER_ID) long memberId,
            @Valid @RequestBody PushTokenRequest request) {
        notificationService.registerDevice(memberId, request.token());
        return ResponseEntity.noContent().build();
    }

    /**
     * 기기 토큰 등록 요청.
     *
     * @param token FCM 등록 토큰
     */
    record PushTokenRequest(@NotBlank @Size(max = TOKEN_MAX) String token) {}
}
