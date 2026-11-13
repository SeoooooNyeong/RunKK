package com.a207.runkk.avatar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.a207.runkk.common.DateRange;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.ItemSlot;
import com.a207.runkk.policy.OperationPolicy;
import com.a207.runkk.policy.ShopCatalog;
import com.a207.runkk.policy.ShopItem;
import com.a207.runkk.running.RunQueryService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 랭킹 줄 회원 모습(S15P31A207-300): 얼굴 키·착용 그림 키·3단계 체형·러닝량을 회원 묶음으로 한 번에 읽는다. */
@DisplayName("여러 회원의 러너 모습 조회")
class RunnerLookQueryTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 13, 21, 0);
    private static final LocalDate TODAY = NOW.toLocalDate();
    private static final DateRange WEEK = new DateRange(TODAY.minusDays(6), TODAY);
    private static final long ALICE = 1;
    private static final long BOB = 2;
    private static final long NEWBIE = 3;

    private final CountingAvatars avatars = new CountingAvatars();
    private final RunQueryService runs = mock(RunQueryService.class);
    private final OperationPolicy policy = mock(OperationPolicy.class);
    private final RunnerLookQuery query =
            new RunnerLookQuery(
                    avatars,
                    runs,
                    policy,
                    AvatarFakes.transactions(),
                    Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL));

    /** 조회 횟수를 세는 인메모리 저장소. */
    static final class CountingAvatars extends AvatarFakes.Avatars {
        int batchReads;

        @Override
        public synchronized List<Avatar> findAllByMemberIds(java.util.Collection<Long> memberIds) {
            batchReads++;
            return super.findAllByMemberIds(memberIds);
        }
    }

    @BeforeEach
    void catalog() {
        when(policy.shopCatalog())
                .thenReturn(
                        new ShopCatalog(
                                List.of(
                                        new ShopItem(
                                                11,
                                                "크루 티",
                                                ItemSlot.TOP,
                                                10,
                                                true,
                                                "top_crew_tee",
                                                null),
                                        new ShopItem(
                                                12,
                                                "레깅스",
                                                ItemSlot.BOTTOM,
                                                10,
                                                true,
                                                "bottom_leggings",
                                                null),
                                        new ShopItem(13, "그림 없는 신발", ItemSlot.SHOES, 10, true),
                                        new ShopItem(
                                                14,
                                                "모자",
                                                ItemSlot.HAT,
                                                10,
                                                false,
                                                "hat_cap",
                                                null))));
        when(runs.distancesByMember(anyCollection(), any())).thenReturn(Map.of());
    }

    @Test
    @DisplayName("착용 부위 → 그 아이템 그림 키, 그림 키가 없거나 목록에 없는 아이템 부위는 빠진다. 판매 중지 아이템도 그대로 보인다")
    void outfitMapsEquippedItemsToAssetKeys() {
        Avatar alice = Avatar.create(ALICE);
        alice.equip(11, ItemSlot.TOP);
        alice.equip(13, ItemSlot.SHOES);
        alice.equip(14, ItemSlot.HAT);
        alice.equip(99, ItemSlot.BOTTOM);
        avatars.save(alice);

        RunnerLook look = query.looksOf(List.of(ALICE)).get(ALICE);

        assertThat(look.outfit())
                .containsExactly(
                        Map.entry(ItemSlot.TOP, "top_crew_tee"),
                        Map.entry(ItemSlot.HAT, "hat_cap"));
    }

    @Test
    @DisplayName("얼굴 키는 Face.renderKey, 고르지 않았으면 null")
    void faceKeyOrNull() {
        Avatar alice = Avatar.create(ALICE);
        alice.chooseDefault(DefaultCharacter.FOX);
        avatars.save(alice);
        avatars.save(Avatar.create(BOB));

        Map<Long, RunnerLook> looks = query.looksOf(List.of(ALICE, BOB));

        assertThat(looks.get(ALICE).faceKey()).isEqualTo("default:FOX");
        assertThat(looks.get(BOB).faceKey()).isNull();
    }

    @Test
    @DisplayName("아바타 행이 없는 회원은 기본값(얼굴 없음·착용 없음·기본 몸)이고 조회가 행을 만들지 않는다")
    void missingAvatarRowGetsDefaultsWithoutSaving() {
        when(runs.distancesByMember(anyCollection(), any()))
                .thenReturn(Map.of(NEWBIE, Distance.ofMeters(3_000)));

        RunnerLook look = query.looksOf(List.of(NEWBIE)).get(NEWBIE);

        assertThat(look).isEqualTo(new RunnerLook(null, Map.of(), 0, 10));
        assertThat(avatars.streamAll()).isEmpty();
    }

    @Test
    @DisplayName("체형은 3단계(STAGE1 → 2), 러닝량은 최근 7일(오늘−6일~오늘) 유효 거리로 계산한다")
    void bodyVariantAndRunAmount() {
        Avatar alice = Avatar.create(ALICE);
        alice.updateAppearance(BodyShape.STAGE1, SkinTone.NIGHT);
        avatars.save(alice);
        Avatar bob = Avatar.create(BOB);
        bob.updateAppearance(BodyShape.STAGE3, SkinTone.DAYTIME);
        avatars.save(bob);
        when(runs.distancesByMember(anyCollection(), any()))
                .thenReturn(
                        Map.of(ALICE, Distance.ofMeters(15_400), BOB, Distance.ofMeters(45_000)));

        Map<Long, RunnerLook> looks = query.looksOf(List.of(ALICE, BOB));

        verify(runs).distancesByMember(Set.of(ALICE, BOB), WEEK);
        assertThat(looks.get(ALICE).bodyVariant()).isEqualTo(2);
        assertThat(looks.get(ALICE).runAmount()).isEqualTo(51);
        assertThat(looks.get(BOB).bodyVariant()).isEqualTo(1);
        assertThat(looks.get(BOB).runAmount()).isEqualTo(100);
    }

    @Test
    @DisplayName("회원 수와 관계없이 아바타 묶음 조회·거리 조회·상점 목록을 한 번씩만 읽는다")
    void readsOnceRegardlessOfMemberCount() {
        avatars.save(Avatar.create(ALICE));
        avatars.save(Avatar.create(BOB));

        Map<Long, RunnerLook> looks = query.looksOf(List.of(ALICE, BOB, NEWBIE, ALICE));

        assertThat(looks).containsOnlyKeys(ALICE, BOB, NEWBIE);
        assertThat(avatars.batchReads).isEqualTo(1);
        verify(runs, times(1)).distancesByMember(anyCollection(), any());
        verify(policy, times(1)).shopCatalog();
    }

    @Test
    @DisplayName("빈 목록이면 아무것도 읽지 않는다")
    void emptyInput() {
        assertThat(query.looksOf(List.of())).isEmpty();
        assertThat(avatars.batchReads).isZero();
        verify(runs, never()).distancesByMember(anyCollection(), any());
        verifyNoInteractions(policy);
    }

    @Test
    @DisplayName("모습 값 범위: 체형 0~2, 러닝량 0~100, 착용 맵은 바꿀 수 없다")
    void lookRanges() {
        assertThatThrownBy(() -> new RunnerLook(null, Map.of(), 3, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RunnerLook(null, Map.of(), 0, 101))
                .isInstanceOf(IllegalArgumentException.class);
        RunnerLook look = new RunnerLook(null, null, 0, 0);
        assertThat(look.outfit()).isEmpty();
        assertThatThrownBy(() -> look.outfit().put(ItemSlot.TOP, "x"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
