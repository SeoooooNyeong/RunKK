package com.a207.runkk.web.admin.notification;

import com.a207.runkk.notification.NoticeInput;
import com.a207.runkk.notification.NoticeResult;
import com.a207.runkk.notification.NoticeService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 운영자 공지 발송(S15P31A207-275, 이벤트 당첨 안내) → NoticeService. 운영자는 web.admin의 OperatorCheckInterceptor가
 * 확인해 요청 속성 actorId로 붙인 계정이고 operator, web.admin을 import하지 않는다(D-50). 형식이 틀리면 400, 링크가 구글 폼 https
 * 주소가 아니면 409 VALUE_INVALID.
 */
@RestController
@RequestMapping("/api/v1/admin/notifications")
public class NoticeAdminController {

    private static final String ACTOR_ID = "actorId";

    private final NoticeService noticeService;

    public NoticeAdminController(NoticeService noticeService) {
        this.noticeService = noticeService;
    }

    @PostMapping("/notices")
    NoticeResult send(
            @RequestAttribute(ACTOR_ID) String actorId, @Valid @RequestBody NoticeRequest request) {
        return noticeService.send(actorId, request.toInput());
    }

    /**
     * 공지 발송 요청.
     *
     * @param memberIds 받을 회원 ID 1~100개(겹치면 한 번만)
     * @param title 제목 1~60자
     * @param body 본문 1~500자
     * @param linkUrl 구글 폼 https 주소(300자 이하), 없거나 빈 글이면 링크 없음
     */
    record NoticeRequest(
            @NotEmpty @Size(max = NoticeService.MAX_RECIPIENTS)
                    List<@NotNull @Positive Long> memberIds,
            @NotBlank @Size(max = 60) String title,
            @NotBlank @Size(max = 500) String body,
            @Size(max = NoticeService.LINK_MAX) String linkUrl) {

        NoticeInput toInput() {
            return new NoticeInput(
                    memberIds,
                    title.strip(),
                    body.strip(),
                    linkUrl == null || linkUrl.isBlank() ? null : linkUrl.strip());
        }
    }
}
