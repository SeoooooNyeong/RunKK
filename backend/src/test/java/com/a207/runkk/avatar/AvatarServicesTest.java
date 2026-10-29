package com.a207.runkk.avatar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.DateRange;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.ItemSlot;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.member.ConsentItem;
import com.a207.runkk.member.MemberQueryService;
import com.a207.runkk.member.MemberWithdrawn;
import com.a207.runkk.notification.NotificationService;
import com.a207.runkk.notification.NotificationType;
import com.a207.runkk.policy.BodyShapeRule;
import com.a207.runkk.policy.FeatureToggles;
import com.a207.runkk.policy.OperationPolicy;
import com.a207.runkk.policy.SelfieLimit;
import com.a207.runkk.policy.ShopCatalog;
import com.a207.runkk.policy.ShopItem;
import com.a207.runkk.policy.SkinToneRule;
import com.a207.runkk.reward.RewardService;
import com.a207.runkk.running.DayStats;
import com.a207.runkk.running.RunQueryService;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** avatar 응용 서비스(14장 단위: 인메모리 저장소·가짜 게이트웨이·고정 시계). SQ-11, srs.md 6.3 6.3·6.6·6.7. */
@DisplayName("avatar 응용 서비스")
class AvatarServicesTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 21, 9, 0);
    private static final LocalDate TODAY = NOW.toLocalDate();
    private static final long ALICE = 1;
    private static final byte[] SELFIE = {1, 2, 3};

    private final AvatarFakes.Avatars avatars = new AvatarFakes.Avatars();
    private final AvatarFakes.Images images = new AvatarFakes.Images();
    private final AvatarFakes.Locks locks = new AvatarFakes.Locks();
    private final AvatarFakes.Failures failures = new AvatarFakes.Failures();
    private final AvatarFakes.Gateway gateway = new AvatarFakes.Gateway();
    private final RewardService rewards = mock(RewardService.class);
    private final MemberQueryService members = mock(MemberQueryService.class);
    private final OperationPolicy policy = mock(OperationPolicy.class);
    private final RunQueryService runs = mock(RunQueryService.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final Clock clock = Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL);
    private final AvatarTransactions tx = AvatarFakes.transactions();

    private AvatarService service;
    private AppearanceService appearance;

    @BeforeEach
    void wire() {
        when(policy.features()).thenReturn(new FeatureToggles(true, true));
        when(policy.selfieLimit()).thenReturn(new SelfieLimit(1));
        when(policy.shopCatalog())
                .thenReturn(
                        new ShopCatalog(
                                List.of(
                                        new ShopItem(10, "모자", ItemSlot.HAT, 30, true),
                                        new ShopItem(11, "상의", ItemSlot.TOP, 30, true))));
        when(policy.bodyShapeRule()).thenReturn(new BodyShapeRule(List.of(0, 1, 3, 5), 4));
        when(policy.skinToneRule())
                .thenReturn(
                        new SkinToneRule(
                                List.of(
                                        LocalTime.of(4, 0),
                                        LocalTime.of(7, 0),
                                        LocalTime.of(11, 0),
                                        LocalTime.of(17, 0),
                                        LocalTime.of(21, 0))));
        when(members.hasConsent(ALICE, ConsentItem.FACE_TRANSFER)).thenReturn(true);
        when(rewards.balanceOf(anyLong())).thenReturn(70);
        SelfieConversion conversion =
                new SelfieConversion(gateway, images, locks, failures, avatars, members, tx);
        BackgroundService backgrounds =
                new BackgroundService(
                        avatars,
                        runs,
                        policy,
                        notifications,
                        tx,
                        new com.a207.runkk.avatar.BackfillProperties(
                                java.time.LocalDateTime.of(2026, 10, 1, 0, 0)));
        when(policy.landmarkTable()).thenReturn(new com.a207.runkk.policy.LandmarkTable(List.of()));
        when(runs.validDistanceOf(anyLong())).thenReturn(Distance.ZERO);
        service =
                new AvatarService(
                        avatars, conversion, rewards, members, policy, backgrounds, tx, clock);
        appearance =
                new AppearanceService(
                        avatars, new AppearanceCalculator(), runs, policy, notifications, tx);
    }

    private static ReasonCode reasonOf(Throwable e) {
        return ((BusinessRuleException) e).reason();
    }

    @Test
    @DisplayName("셀카 성공: 부위 이미지 저장 키로 얼굴을 바꾸고 오늘은 다시 만들 수 없다, 잠금은 풀린다")
    void selfie_success() {
        AvatarResult result = service.createFromSelfie(ALICE, SELFIE);

        assertThat(result.created()).isTrue();
        assertThat(result.view().faceKey()).isEqualTo("selfie:face/1/s1");
        assertThat(result.view().canCreateSelfieToday()).isFalse();
        assertThat(result.view().balance()).isEqualTo(70);
        assertThat(locks.held).isEmpty();
        assertThatThrownBy(() -> service.createFromSelfie(ALICE, SELFIE))
                .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(ReasonCode.SELFIE_LIMIT));
        assertThat(gateway.calls).as("한도 거부는 ai를 부르지 않는다").isEqualTo(1);
    }

    @Test
    @DisplayName("변환 실패: 사유 + 기본 캐릭터 안내, 생성 횟수 그대로라 다시 시도할 수 있다")
    void selfie_failure_keepsCount() {
        gateway.answer = image -> FaceTransformOutcome.failure(FailureReason.NO_FACE);

        AvatarResult result = service.createFromSelfie(ALICE, SELFIE);

        assertThat(result.created()).isFalse();
        assertThat(result.failure()).isEqualTo(FailureReason.NO_FACE);
        assertThat(result.view().guide()).isNotNull();
        assertThat(result.view().canCreateSelfieToday()).isTrue();
        assertThat(images.saved).isEmpty();
    }

    @Test
    @DisplayName("변환 실패가 하루 5번이면 SELFIE_RETRY_LIMIT, ai를 더 부르지 않는다")
    void selfie_failureCap() {
        gateway.answer = image -> FaceTransformOutcome.failure(FailureReason.NO_FACE);
        for (int i = 0; i < SelfieConversion.MAX_FAILURES_PER_DAY; i++) {
            assertThat(service.createFromSelfie(ALICE, SELFIE).created()).isFalse();
        }
        assertThatThrownBy(() -> service.createFromSelfie(ALICE, SELFIE))
                .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(ReasonCode.SELFIE_RETRY_LIMIT));
        assertThat(gateway.calls).isEqualTo(SelfieConversion.MAX_FAILURES_PER_DAY);
        assertThat(locks.held).isEmpty();
    }

    @Test
    @DisplayName("변환하는 사이 탈퇴(동의가 지워짐)하면 부위 이미지를 남기지 않는다")
    void selfie_withdrawnDuringConversion_leavesNoImages() {
        gateway.answer =
                image -> {
                    when(members.hasConsent(ALICE, ConsentItem.FACE_TRANSFER)).thenReturn(false);
                    return FaceTransformOutcome.success(AvatarFakes.images());
                };
        assertThatThrownBy(() -> service.createFromSelfie(ALICE, SELFIE))
                .satisfies(
                        e -> assertThat(reasonOf(e)).isEqualTo(ReasonCode.FACE_CONSENT_REQUIRED));
        assertThat(images.saved).isEmpty();
        assertThat(locks.held).isEmpty();
    }

    @Test
    @DisplayName("거부: 축소 운영 → 동의 없음 → 진행 중, ai를 부르지 않는다")
    void selfie_rejections() {
        when(policy.features()).thenReturn(new FeatureToggles(false, true));
        assertThatThrownBy(() -> service.createFromSelfie(ALICE, SELFIE))
                .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(ReasonCode.SELFIE_DISABLED));

        when(policy.features()).thenReturn(new FeatureToggles(true, true));
        when(members.hasConsent(ALICE, ConsentItem.FACE_TRANSFER)).thenReturn(false);
        assertThatThrownBy(() -> service.createFromSelfie(ALICE, SELFIE))
                .satisfies(
                        e -> assertThat(reasonOf(e)).isEqualTo(ReasonCode.FACE_CONSENT_REQUIRED));

        when(members.hasConsent(ALICE, ConsentItem.FACE_TRANSFER)).thenReturn(true);
        locks.tryLock(ALICE, Duration.ofSeconds(30));
        assertThatThrownBy(() -> service.createFromSelfie(ALICE, SELFIE))
                .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(ReasonCode.SELFIE_IN_PROGRESS));
        assertThat(gateway.calls).isZero();
    }

    @Test
    @DisplayName("기본 캐릭터 선택과 착용: 보유·부위가 맞을 때만 착용, 아니면 부위별 거부 사유, null은 해제")
    void chooseDefaultAndEquip() {
        assertThat(service.chooseDefault(ALICE, DefaultCharacter.FOX).view().faceKey())
                .isEqualTo("default:FOX");
        when(rewards.owns(ALICE, 10)).thenReturn(true);
        when(rewards.owns(ALICE, 11)).thenReturn(false);

        AvatarView view =
                service.equip(
                        ALICE,
                        List.of(
                                EquipChange.wear(ItemSlot.HAT, 10),
                                EquipChange.wear(ItemSlot.TOP, 11),
                                EquipChange.wear(ItemSlot.SHOES, 10)));

        assertThat(view.equipped()).containsExactlyEntriesOf(Map.of(ItemSlot.HAT, 10L));
        assertThat(view.rejected())
                .containsOnlyKeys(ItemSlot.TOP, ItemSlot.SHOES)
                .containsValue(ReasonCode.ITEM_NOT_OWNED);
        assertThat(service.equip(ALICE, List.of(EquipChange.takeOff(ItemSlot.HAT))).equipped())
                .isEmpty();
        assertThat(new AvatarQuery(avatars).faceKey(ALICE)).contains("default:FOX");
        assertThat(new AvatarQuery(avatars).faceKey(99)).isEmpty();
    }

    @Test
    @DisplayName("0시 외형 재계산: 체형이 바뀐 회원만 알림, 바뀐 수를 돌려준다, 러닝 없으면 피부색 그대로")
    void refreshAll() {
        avatars.save(Avatar.create(ALICE));
        avatars.save(Avatar.create(2));
        when(runs.dailyStats(eq(ALICE), any(DateRange.class)))
                .thenReturn(
                        Map.of(
                                TODAY.minusDays(1),
                                new DayStats(
                                        TODAY.minusDays(1),
                                        Distance.ofKm(5),
                                        Duration.ofMinutes(30),
                                        3)));
        when(runs.recentStartTimes(ALICE, 10))
                .thenReturn(List.of(TODAY.minusDays(1).atTime(5, 30)));
        when(runs.dailyStats(eq(2L), any(DateRange.class))).thenReturn(Map.of());
        when(runs.recentStartTimes(2L, 10)).thenReturn(List.of());

        assertThat(appearance.refreshAll(TODAY)).isEqualTo(1);

        assertThat(avatars.findOrCreate(ALICE).bodyShape()).isEqualTo(BodyShape.STAGE3);
        assertThat(avatars.findOrCreate(ALICE).skinTone()).isEqualTo(SkinTone.DAWN);
        assertThat(avatars.findOrCreate(2).skinTone()).isEqualTo(SkinTone.DAYTIME);
        verify(notifications, times(1))
                .notify(argThat(r -> r.type() == NotificationType.BODY_SHAPE_CHANGED));
        verify(runs).dailyStats(ALICE, new DateRange(TODAY.minusDays(7), TODAY.minusDays(1)));
    }

    @Test
    @DisplayName("한 회원의 재계산이 실패해도 다음 회원은 계속한다")
    void refreshAll_isolatesFailures() {
        avatars.save(Avatar.create(ALICE));
        avatars.save(Avatar.create(2));
        when(runs.dailyStats(eq(ALICE), any(DateRange.class)))
                .thenThrow(new IllegalStateException("고장"));
        when(runs.dailyStats(eq(2L), any(DateRange.class))).thenReturn(Map.of());
        when(runs.recentStartTimes(anyLong(), anyInt())).thenReturn(List.of());

        assertThat(appearance.refreshAll(TODAY)).isZero();
        verify(runs).recentStartTimes(2L, 10);
        verify(notifications, never()).notify(any());
    }

    @Test
    @DisplayName("부위 이미지: 본인·다른 회원 모두 읽고, 탈퇴한 회원·다른 형식 키는 거부")
    void partImage() {
        service.createFromSelfie(ALICE, SELFIE);
        String key = images.saved.get(ALICE).parts().get(FacePart.EYES);
        FaceImageService faces = new FaceImageService(images, members);
        when(members.disclosureOf(anyLong(), any()))
                .thenAnswer(
                        inv -> {
                            Map<Long, com.a207.runkk.member.DisclosureLevel> map =
                                    new java.util.HashMap<>();
                            for (Object id : (java.util.Collection<?>) inv.getArgument(1)) {
                                map.put(
                                        (Long) id,
                                        (Long) id == ALICE
                                                ? com.a207.runkk.member.DisclosureLevel.PUBLIC
                                                : com.a207.runkk.member.DisclosureLevel.WITHDRAWN);
                            }
                            return map;
                        });
        assertThat(faces.partImage(2L, key)).isNotEmpty();
        assertThatThrownBy(() -> faces.partImage(2L, "face/3/x/EYES"))
                .isInstanceOf(com.a207.runkk.common.PermissionDeniedException.class);
        assertThatThrownBy(() -> faces.partImage(2L, "../etc/passwd"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> faces.partImage(ALICE, "face/" + ALICE + "/none/EYES"))
                .extracting(e -> ((BusinessRuleException) e).reason())
                .isEqualTo(ReasonCode.FACE_IMAGE_NOT_FOUND);
    }

    @Test
    @DisplayName("탈퇴하면 아바타와 부위 이미지를 지운다")
    void withdrawal() {
        service.createFromSelfie(ALICE, SELFIE);
        new AvatarWithdrawalListener(avatars, images)
                .onMemberWithdrawn(new MemberWithdrawn(ALICE, NOW));
        assertThat(images.saved).isEmpty();
        assertThat(new AvatarQuery(avatars).faceKey(ALICE)).isEmpty();
        assertThat(Optional.of(avatars.findOrCreate(ALICE).creationCount())).contains(0);
    }
}
