package com.a207.runkk.avatar;

import com.a207.runkk.common.Distance;
import com.a207.runkk.notification.NavigationTarget;
import com.a207.runkk.notification.NotificationRequest;
import com.a207.runkk.notification.NotificationService;
import com.a207.runkk.notification.NotificationType;
import com.a207.runkk.notification.Screen;
import com.a207.runkk.policy.Landmark;
import com.a207.runkk.policy.LandmarkStage;
import com.a207.runkk.policy.LandmarkTable;
import com.a207.runkk.policy.OperationPolicy;
import com.a207.runkk.running.RunQueryService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;

/**
 * 랜드마크 배경의 해금·선택 유스케이스(설계 6.1.8, 6.2 A-26, FR-201, FR-202, D-107). 누적 유효 거리는
 * RunQueryService.validDistanceOf로 읽고(D-139) 개인 단계(DOMESTIC·EAST_ASIA·INTERCONTINENTAL) 랜드마크 22개만
 * 배경이다. 해금은 회원마다 새 트랜잭션에서 아바타 행을 잠그고 하며, 리스너·보정 배치·조회 때 채우기가 겹쳐 avatar_background 기본 키나 아바타 버전이
 * 충돌하면 다시 읽어 최대 {@value #MAX_ATTEMPTS}회 한다(5.3). 알림은 새로 해금한 것이 있을 때 한 건이고 이름을 거리 순으로 모두 적는다(D-148).
 * 알림 저장은 해금과 같은 트랜잭션이라 충돌로 다시 하면 알림도 함께 되돌려진다.
 */
@Service
public class BackgroundService {

    /** 보정 배치가 거슬러 볼 날 수(오늘 0시에서). 리워드·티어 보정과 같다(M4). */
    static final int LOOKBACK_DAYS = 2;

    /** 해금 충돌 재시도 횟수(5.3과 같다). */
    static final int MAX_ATTEMPTS = 3;

    private static final Set<LandmarkStage> PERSONAL_STAGES =
            EnumSet.of(
                    LandmarkStage.DOMESTIC,
                    LandmarkStage.EAST_ASIA,
                    LandmarkStage.INTERCONTINENTAL);

    private static final Logger log = LoggerFactory.getLogger(BackgroundService.class);

    private final AvatarRepository avatarRepository;
    private final RunQueryService runQueryService;
    private final OperationPolicy operationPolicy;
    private final NotificationService notificationService;
    private final AvatarTransactions transactions;
    private final BackfillProperties backfill;

    BackgroundService(
            AvatarRepository avatarRepository,
            RunQueryService runQueryService,
            OperationPolicy operationPolicy,
            NotificationService notificationService,
            AvatarTransactions transactions,
            BackfillProperties backfill) {
        this.avatarRepository = avatarRepository;
        this.runQueryService = runQueryService;
        this.operationPolicy = operationPolicy;
        this.notificationService = notificationService;
        this.transactions = transactions;
        this.backfill = backfill;
    }

    /**
     * 누적 유효 거리 ≥ 랜드마크 거리인 개인 랜드마크로 Avatar.unlock. 새로 해금한 것이 있으면 BACKGROUND_UNLOCKED 알림 한 건(새로 해금한
     * 배경 이름 모두, 거리 오름차순, D-148). 새로 해금한 것을 돌려준다.
     */
    public List<UnlockedBackground> unlockReached(long memberId, LocalDateTime now) {
        return fill(
                memberId,
                now,
                runQueryService.validDistanceOf(memberId),
                added -> {
                    if (!added.isEmpty()) {
                        notificationService.notify(notificationOf(memberId, added));
                    }
                });
    }

    /**
     * unlockReached와 같은 판정(누적 유효 거리 전체)으로 해금하되 알림을 내지 않는다. v0.10 일회성 재판정으로 무효 기록이 유효가 되어 늘어난 거리로 넘은
     * 랜드마크를 채울 때 쓴다(사용자 결정: 재판정은 푸시를 보내지 않는다). 이미 해금한 배경은 다시 넣지 않는다.
     */
    public List<UnlockedBackground> unlockReachedQuietly(long memberId, LocalDateTime now) {
        return fill(memberId, now, runQueryService.validDistanceOf(memberId), added -> {});
    }

    /**
     * unlockReached와 같은 판정으로 해금만 채우고 알림을 내지 않는다(배포 전에 넘은 랜드마크, D-107 (3)). 해금 행이 하나도 없는 회원에게만 채운다:
     * V25는 해금 행을 만들지 않으므로 배포 전 상태는 "행 없음"이다. 판정 거리는 배포 전 거리(저장 일시가 backfill-cutoff 이전인 유효 기록,
     * validDistanceBefore)로 한정하고, 그 뒤 거리로 넘은 랜드마크는 리스너·보정 배치가 알림과 함께 넣는다. 그래서 기록 저장 직후의 조회가 비동기
     * 리스너보다 먼저 와도 방금 넘은 첫 랜드마크의 알림을 삼키지 않는다(리뷰 H1, BE5-A-4, D-176).
     */
    public List<UnlockedBackground> backfillReached(long memberId, LocalDateTime now) {
        boolean firstFill =
                transactions.read(
                        () ->
                                avatarRepository
                                        .findOrCreate(memberId)
                                        .unlockedBackgrounds()
                                        .isEmpty());
        if (!firstFill) {
            return List.of();
        }
        Distance beforeDeploy =
                runQueryService.validDistanceBefore(memberId, backfill.backfillCutoff());
        return fill(memberId, now, beforeDeploy, added -> {});
    }

    /**
     * GET /avatar/backgrounds. 먼저 알림 없이 해금을 채운 뒤(채우기 실패는 로그만 남기고 목록은 돌려준다) 현재 기준값의 개인 랜드마크 순서, 그 뒤에
     * 표에서 빠졌지만 이미 해금한 배경 순서로 목록을 만든다.
     */
    public BackgroundsView backgrounds(long memberId, LocalDateTime now) {
        try {
            backfillReached(memberId, now);
        } catch (RuntimeException e) {
            log.warn(
                    "background_backfill_failed memberId={} error={}",
                    memberId,
                    e.getClass().getSimpleName(),
                    e);
        }
        return transactions.read(
                () ->
                        viewOf(
                                avatarRepository.findOrCreate(memberId),
                                operationPolicy.landmarkTable()));
    }

    /**
     * PUT /avatar/background(key 있음). Avatar.selectBackground(잠긴 키면 BACKGROUND_LOCKED) → 저장 → 목록.
     */
    public BackgroundsView choose(long memberId, String key) {
        return changeSelection(memberId, avatar -> avatar.selectBackground(key));
    }

    /** PUT /avatar/background(key 없음). Avatar.useDefaultBackground → 저장 → 목록. */
    public BackgroundsView useDefault(long memberId) {
        return changeSelection(memberId, Avatar::useDefaultBackground);
    }

    private BackgroundsView changeSelection(long memberId, Consumer<Avatar> change) {
        return retrying(
                () ->
                        transactions.write(
                                () -> {
                                    Avatar avatar =
                                            avatarRepository.findForUpdate(memberId).orElseThrow();
                                    change.accept(avatar);
                                    avatarRepository.save(avatar);
                                    return viewOf(avatar, operationPolicy.landmarkTable());
                                }));
    }

    /**
     * BackgroundReconcileJob(0시 10분). (오늘 − {@value #LOOKBACK_DAYS}일) 0시부터 now까지 기록을 저장한 회원마다
     * unlockReached로 커밋 뒤 수신자 실패를 보정한다(D-107 (3), D-139). 한 회원의 실패는 로그 뒤 다음 회원이다. 처리한 회원 수를 돌려준다.
     */
    public int reconcile(LocalDateTime now) {
        LocalDateTime from = now.toLocalDate().minusDays(LOOKBACK_DAYS).atStartOfDay();
        int processed = 0;
        for (long memberId : runQueryService.memberIdsSavedBetween(from, now)) {
            try {
                unlockReached(memberId, now);
                processed++;
            } catch (RuntimeException e) {
                log.error(
                        "background_reconcile_failed memberId={} error={}",
                        memberId,
                        e.getClass().getSimpleName(),
                        e);
            }
        }
        log.info("background_reconciled from={} members={}", from, processed);
        return processed;
    }

    /** 알림 여부만 다른 해금 판정 하나(D-36: 불리언 매개변수로 알림 여부를 고르지 않는다). */
    private List<UnlockedBackground> fill(
            long memberId,
            LocalDateTime now,
            Distance total,
            Consumer<List<UnlockedBackground>> afterUnlock) {
        List<Landmark> reached =
                personal(operationPolicy.landmarkTable()).stream()
                        .filter(l -> total.isAtLeast(l.distance()))
                        .toList();
        if (reached.isEmpty() || alreadyUnlocked(memberId, reached)) {
            return List.of();
        }
        return retrying(
                () ->
                        transactions.write(
                                () -> {
                                    Avatar avatar =
                                            avatarRepository.findForUpdate(memberId).orElseThrow();
                                    List<UnlockedBackground> added = avatar.unlock(reached, now);
                                    avatarRepository.save(avatar);
                                    afterUnlock.accept(added);
                                    return added;
                                }));
    }

    /** 잠그기 전에 읽기만으로 확인한다: 넘은 랜드마크를 모두 해금했으면 쓰기 트랜잭션을 열지 않는다(조회 때 채우기의 대부분). */
    private boolean alreadyUnlocked(long memberId, List<Landmark> reached) {
        Set<String> have =
                transactions.read(
                        () ->
                                avatarRepository
                                        .findOrCreate(memberId)
                                        .unlockedBackgrounds()
                                        .stream()
                                        .map(UnlockedBackground::landmarkKey)
                                        .collect(Collectors.toSet()));
        return reached.stream().allMatch(l -> have.contains(l.key()));
    }

    /** 같은 회원의 해금·선택이 겹쳐 기본 키·버전·잠금이 충돌하면 새 트랜잭션으로 다시 읽어 최대 3회 한다. */
    private static <T> T retrying(Supplier<T> work) {
        for (int attempt = 1; attempt < MAX_ATTEMPTS; attempt++) {
            try {
                return work.get();
            } catch (DataIntegrityViolationException
                    | OptimisticLockingFailureException
                    | PessimisticLockingFailureException e) {
                log.info(
                        "background_unlock_retry attempt={} error={}",
                        attempt,
                        e.getClass().getSimpleName());
            }
        }
        return work.get(); // 마지막 시도는 예외를 그대로 올린다
    }

    private static List<Landmark> personal(LandmarkTable table) {
        return table.landmarks().stream().filter(l -> PERSONAL_STAGES.contains(l.stage())).toList();
    }

    private static NotificationRequest notificationOf(
            long memberId, List<UnlockedBackground> added) {
        String names =
                added.stream()
                        .sorted(Comparator.comparing(UnlockedBackground::distance))
                        .map(UnlockedBackground::landmarkName)
                        .collect(Collectors.joining(", "));
        return new NotificationRequest(
                memberId,
                NotificationType.BACKGROUND_UNLOCKED,
                "새 배경 " + added.size() + "개를 해금했어요: " + names,
                new NavigationTarget(Screen.AVATAR_BACKGROUND, null));
    }

    private static BackgroundsView viewOf(Avatar avatar, LandmarkTable table) {
        Map<String, UnlockedBackground> unlocked = new HashMap<>();
        avatar.unlockedBackgrounds().forEach(u -> unlocked.put(u.landmarkKey(), u));
        List<BackgroundsView.Item> items = new ArrayList<>();
        Set<String> listed = new HashSet<>();
        for (Landmark l : personal(table)) {
            UnlockedBackground u = unlocked.get(l.key());
            listed.add(l.key());
            items.add(
                    new BackgroundsView.Item(
                            l.key(),
                            l.name(),
                            l.stage().name(),
                            l.distance(),
                            u != null,
                            u == null ? null : u.unlockedAt()));
        }
        unlocked.values().stream()
                .filter(u -> !listed.contains(u.landmarkKey()))
                .sorted(
                        Comparator.comparing(UnlockedBackground::distance)
                                .thenComparing(UnlockedBackground::landmarkKey))
                .forEach(
                        u ->
                                items.add(
                                        new BackgroundsView.Item(
                                                u.landmarkKey(),
                                                u.landmarkName(),
                                                u.stage().name(),
                                                u.distance(),
                                                true,
                                                u.unlockedAt())));
        return new BackgroundsView(avatar.selectedBackgroundKey().orElse(null), items);
    }
}
