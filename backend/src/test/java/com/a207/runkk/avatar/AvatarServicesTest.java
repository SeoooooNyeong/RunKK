package com.a207.runkk.avatar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.a207.runkk.common.BusinessRuleException;
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
import com.a207.runkk.running.RunQueryService;
import java.time.Clock;
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

    private final AvatarFakes.Avatars avatars = new AvatarFakes.Avatars();
    private final AvatarFakes.Images images = new AvatarFakes.Images();
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
        service = new AvatarService(avatars, rewards, policy, backgrounds, tx, clock);
        appearance =
                new AppearanceService(
                        avatars, new AppearanceCalculator(), runs, policy, notifications, tx);
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
        assertThat(new AvatarQuery(avatars, null).faceKey(ALICE)).contains("default:FOX");
        assertThat(new AvatarQuery(avatars, null).faceKey(99)).isEmpty();
    }

    @Test
    @DisplayName("0시 외형 재계산: 최근에 달린 회원은 기본 몸, 쉰 회원만 한 단계 통통해져 알림, 달린 적 없으면 기본 몸·피부색 그대로")
    void refreshAll() {
        when(policy.bodyShapeReleased()).thenReturn(true);
        avatars.save(Avatar.create(ALICE));
        avatars.save(Avatar.create(2));
        avatars.save(Avatar.create(3));
        when(runs.recentStartTimes(ALICE, 10))
                .thenReturn(List.of(TODAY.minusDays(1).atTime(5, 30)));
        when(runs.recentStartTimes(2L, 10)).thenReturn(List.of(TODAY.minusDays(10).atTime(12, 0)));
        // 달린 적 없는 회원은 기본 몸 그대로다(D-844)
        when(runs.recentStartTimes(3L, 10)).thenReturn(List.of());

        assertThat(appearance.refreshAll(TODAY)).isEqualTo(1);

        assertThat(avatars.findOrCreate(ALICE).bodyShape()).isEqualTo(BodyShape.STAGE4);
        assertThat(avatars.findOrCreate(ALICE).skinTone()).isEqualTo(SkinTone.DAWN);
        assertThat(avatars.findOrCreate(2).bodyShape()).isEqualTo(BodyShape.STAGE3);
        assertThat(avatars.findOrCreate(2).skinTone()).isEqualTo(SkinTone.DAYTIME);
        assertThat(avatars.findOrCreate(3).bodyShape()).isEqualTo(BodyShape.STAGE4);
        assertThat(avatars.findOrCreate(3).skinTone()).isEqualTo(SkinTone.DAYTIME);
        verify(notifications, times(1))
                .notify(
                        argThat(
                                r ->
                                        r.type() == NotificationType.BODY_SHAPE_CHANGED
                                                && r.recipientId() == 2L));
    }

    @Test
    @DisplayName("체형 변화 미공개(BODY_SHAPE_RELEASED 꺼짐): 쉰 회원도 기본 몸, 이미 통통한 몸은 알림 없이 기본 몸으로, 피부색은 계산한다")
    void refreshAll_bodyShapeClosed() {
        when(policy.bodyShapeReleased()).thenReturn(false);
        avatars.save(Avatar.create(ALICE));
        Avatar fuller = Avatar.create(2);
        fuller.updateAppearance(BodyShape.STAGE2, SkinTone.DAYTIME);
        avatars.save(fuller);
        when(runs.recentStartTimes(ALICE, 10))
                .thenReturn(List.of(TODAY.minusDays(10).atTime(5, 30)));
        when(runs.recentStartTimes(2L, 10)).thenReturn(List.of(TODAY.minusDays(10).atTime(12, 0)));

        appearance.refreshAll(TODAY);

        assertThat(avatars.findOrCreate(ALICE).bodyShape()).isEqualTo(BodyShape.STAGE4);
        assertThat(avatars.findOrCreate(ALICE).skinTone()).isEqualTo(SkinTone.DAWN);
        assertThat(avatars.findOrCreate(2).bodyShape()).isEqualTo(BodyShape.STAGE4);
        verify(notifications, never()).notify(any());
    }

    @Test
    @DisplayName("한 회원의 재계산이 실패해도 다음 회원은 계속한다")
    void refreshAll_isolatesFailures() {
        avatars.save(Avatar.create(ALICE));
        avatars.save(Avatar.create(2));
        when(runs.recentStartTimes(ALICE, 10)).thenThrow(new IllegalStateException("고장"));
        when(runs.recentStartTimes(2L, 10)).thenReturn(List.of(TODAY.minusDays(1).atTime(12, 0)));

        assertThat(appearance.refreshAll(TODAY)).isZero();
        verify(runs).recentStartTimes(2L, 10);
        verify(notifications, never()).notify(any());
    }

    @Test
    @DisplayName("부위 이미지: 본인·다른 회원 모두 읽고, 탈퇴한 회원·다른 형식 키는 거부")
    void partImage() {
        avatars.findForUpdate(ALICE)
                .orElseThrow()
                .applySelfieFace(
                        images.save(ALICE, AvatarFakes.images()), TODAY, new SelfieLimit(1));
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
        avatars.findForUpdate(ALICE)
                .orElseThrow()
                .applySelfieFace(
                        images.save(ALICE, AvatarFakes.images()), TODAY, new SelfieLimit(1));
        new AvatarWithdrawalListener(avatars, images)
                .onMemberWithdrawn(new MemberWithdrawn(ALICE, NOW));
        assertThat(images.saved).isEmpty();
        assertThat(new AvatarQuery(avatars, null).faceKey(ALICE)).isEmpty();
        assertThat(Optional.of(avatars.findOrCreate(ALICE).creationCount())).contains(0);
    }
}
