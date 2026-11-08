package com.a207.runkk.notification;

import com.a207.runkk.audit.ActorRole;
import com.a207.runkk.audit.AuditAction;
import com.a207.runkk.audit.AuditEntry;
import com.a207.runkk.audit.AuditService;
import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.ReasonCode;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운영자 공지 발송(S15P31A207-275, 이벤트 당첨 안내). 고른 회원마다 NOTICE 알림을 기존 알림 경로(NotificationService.deliver:
 * 알림함 저장, 커밋 뒤 FCM 푸시)로 보내고 감사 기록 한 건을 남긴다. 감사 행위는 새로 만들지 않고 SETTING_CHANGE에 대상 "NOTICE:제목"을 쓴다
 * (develop V37이 audit_record 행위 제약을 다시 만들므로 새 행위를 더하면 prod에서 V37이 깨진다. 이벤트 관리와 같은 이유). 링크는 구글 폼
 * https 주소만 받는다(운영자 계정이 새어도 아무 주소로 회원을 보내지 못하게).
 */
@Service
public class NoticeService {

    /** 한 번에 보낼 수 있는 회원 수. 추첨 당첨자(코치 2명, 베타 10명)에 넉넉하고 전체 발송은 막는다. */
    public static final int MAX_RECIPIENTS = 100;

    /** 링크 주소 길이 상한(notification.target_id 열, V35_3). */
    public static final int LINK_MAX = 300;

    private static final String AUDIT_TARGET_PREFIX = "NOTICE:";
    private static final int AUDIT_TARGET_MAX = 100;

    private final NotificationService notificationService;
    private final AuditService auditService;

    public NoticeService(NotificationService notificationService, AuditService auditService) {
        this.notificationService = notificationService;
        this.auditService = auditService;
    }

    /**
     * 공지를 보낸다. 링크가 구글 폼 https 주소가 아니면 VALUE_INVALID(409). 받는 회원이 비었거나 100명을 넘으면
     * IllegalArgumentException(웹 형식 검사가 먼저 막는다). 없거나 탈퇴한 회원은 건너뛰고 결과에 적는다.
     */
    @Transactional
    public NoticeResult send(String actorId, NoticeInput in) {
        Set<Long> ids = new LinkedHashSet<>(in.memberIds());
        if (ids.isEmpty() || ids.size() > MAX_RECIPIENTS) {
            throw new IllegalArgumentException("받는 회원은 1~" + MAX_RECIPIENTS + "명이다");
        }
        if (in.linkUrl() != null && !isFormLink(in.linkUrl())) {
            throw new BusinessRuleException(ReasonCode.VALUE_INVALID);
        }
        NavigationTarget target = new NavigationTarget(Screen.EXTERNAL_LINK, in.linkUrl());
        List<Long> skipped = new ArrayList<>();
        for (long id : ids) {
            boolean stored =
                    notificationService.deliver(
                            new NotificationRequest(
                                    id, NotificationType.NOTICE, in.body(), target, in.title()));
            if (!stored) {
                skipped.add(id);
            }
        }
        NoticeResult result = new NoticeResult(ids.size(), ids.size() - skipped.size(), skipped);
        String auditTarget = AUDIT_TARGET_PREFIX + in.title();
        auditService.record(
                new AuditEntry(
                        actorId,
                        ActorRole.OPERATOR,
                        AuditAction.SETTING_CHANGE,
                        auditTarget.length() > AUDIT_TARGET_MAX
                                ? auditTarget.substring(0, AUDIT_TARGET_MAX)
                                : auditTarget,
                        null,
                        in + " " + result));
        return result;
    }

    /**
     * 구글 폼 주소인가: https, 사용자 정보와 포트 없음, 호스트 forms.gle(경로 있음) 또는 docs.google.com의 /forms/ 아래. 300자
     * 이하, 공백 없음.
     */
    static boolean isFormLink(String url) {
        if (url.length() > LINK_MAX || url.chars().anyMatch(Character::isWhitespace)) {
            return false;
        }
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            return false;
        }
        if (!"https".equals(uri.getScheme())
                || uri.getRawUserInfo() != null
                || uri.getPort() != -1
                || uri.getHost() == null) {
            return false;
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        String path = uri.getRawPath() == null ? "" : uri.getRawPath();
        return ("forms.gle".equals(host) && path.length() > 1)
                || ("docs.google.com".equals(host) && path.startsWith("/forms/"));
    }
}
