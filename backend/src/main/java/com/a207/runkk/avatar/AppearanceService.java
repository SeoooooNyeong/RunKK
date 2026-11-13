package com.a207.runkk.avatar;

import com.a207.runkk.notification.NavigationTarget;
import com.a207.runkk.notification.NotificationRequest;
import com.a207.runkk.notification.NotificationService;
import com.a207.runkk.notification.NotificationType;
import com.a207.runkk.notification.Screen;
import com.a207.runkk.policy.OperationPolicy;
import com.a207.runkk.running.RunQueryService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 매일 0시 외형 재계산(설계 6.1.8, srs.md 6.3 6.7, FR-163~FR-165). batch.avatar의 AppearanceRefreshJob이 부른다.
 * 회원마다 따로 트랜잭션을 열어 한 회원의 실패가 다른 회원을 막지 않는다.
 */
@Service
public class AppearanceService {

    /** 피부색을 정하는 최근 유효 러닝 수. */
    static final int RECENT_RUNS = 10;

    private static final String SHAPE_CHANGED_TEXT = "러닝 습관에 따라 체형이 바뀌었어요";

    private final AvatarRepository avatarRepository;
    private final AppearanceCalculator appearanceCalculator;
    private final RunQueryService runQueryService;
    private final OperationPolicy operationPolicy;
    private final NotificationService notificationService;
    private final AvatarTransactions transactions;

    AppearanceService(
            AvatarRepository avatarRepository,
            AppearanceCalculator appearanceCalculator,
            RunQueryService runQueryService,
            OperationPolicy operationPolicy,
            NotificationService notificationService,
            AvatarTransactions transactions) {
        this.avatarRepository = avatarRepository;
        this.appearanceCalculator = appearanceCalculator;
        this.runQueryService = runQueryService;
        this.operationPolicy = operationPolicy;
        this.notificationService = notificationService;
        this.transactions = transactions;
    }

    /**
     * 회원마다 마지막 유효 러닝 일시·이전 체형으로 체형을, 최근 유효 10회 시작 시각으로 피부색을 정해 Avatar.updateAppearance. 체형이 바뀌면
     * BODY_SHAPE_CHANGED 알림. 바뀐 수를 돌려준다. 러닝이 없으면 피부색은 그대로다.
     */
    public int refreshAll(LocalDate today) {
        List<Long> memberIds =
                transactions.read(
                        () -> avatarRepository.streamAll().map(Avatar::memberId).toList());
        int changed = 0;
        for (long memberId : memberIds) {
            if (transactions.once("appearance", () -> refresh(memberId, today)).orElse(false)) {
                changed++;
            }
        }
        return changed;
    }

    private boolean refresh(long memberId, LocalDate today) {
        List<LocalDateTime> starts = runQueryService.recentStartTimes(memberId, RECENT_RUNS);
        LocalDateTime last = starts.stream().max(Comparator.naturalOrder()).orElse(null);
        Avatar avatar = avatarRepository.findForUpdate(memberId).orElseThrow();
        // 체형 변화 미공개면 기본 몸으로 두고 알리지 않는다([임시:D-성-218])
        boolean shapeReleased = operationPolicy.bodyShapeReleased();
        BodyShape shape =
                shapeReleased
                        ? appearanceCalculator.bodyShape(
                                new AppearanceInput(last, avatar.bodyShape(), today),
                                operationPolicy.bodyShapeRule())
                        : BodyShape.STAGE4;
        SkinTone tone =
                starts.isEmpty()
                        ? avatar.skinTone()
                        : appearanceCalculator.skinTone(starts, operationPolicy.skinToneRule());
        boolean changed = avatar.updateAppearance(shape, tone);
        avatarRepository.save(avatar);
        if (changed && shapeReleased) {
            notificationService.notify(
                    new NotificationRequest(
                            memberId,
                            NotificationType.BODY_SHAPE_CHANGED,
                            SHAPE_CHANGED_TEXT,
                            new NavigationTarget(Screen.AVATAR, null)));
        }
        return changed;
    }
}
