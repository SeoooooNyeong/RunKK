package com.a207.runkk.avatar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.member.ConsentItem;
import com.a207.runkk.member.MemberQueryService;
import com.a207.runkk.notification.NotificationRequest;
import com.a207.runkk.notification.NotificationService;
import com.a207.runkk.notification.NotificationType;
import com.a207.runkk.notification.Screen;
import com.a207.runkk.policy.FeatureToggles;
import com.a207.runkk.policy.Landmark;
import com.a207.runkk.policy.LandmarkStage;
import com.a207.runkk.policy.LandmarkTable;
import com.a207.runkk.policy.OperationPolicy;
import com.a207.runkk.policy.SelfieLimit;
import com.a207.runkk.policy.SettingKey;
import com.a207.runkk.reward.RewardService;
import com.a207.runkk.running.RunMode;
import com.a207.runkk.running.RunQueryService;
import com.a207.runkk.running.RunRecordSaved;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * BackgroundService·BackgroundUnlockListener와 AvatarService.view의 배경 채우기(6.1.8, 6.2 A-26,
 * D-107·D-129·D-139·D-148). 02-01 2.1.10.15 TC-6156~TC-6163. 인메모리 아바타 저장소·고정 시계, 기본 랜드마크 표.
 */
@DisplayName("랜드마크 배경 해금·선택")
class BackgroundServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime N = LocalDateTime.of(2026, 10, 22, 0, 10);
    private static final long M = 3;
    private static final LandmarkTable DEFAULTS =
            (LandmarkTable) SettingKey.LANDMARKS.parse(SettingKey.LANDMARKS.defaultValue());

    private final AvatarFakes.Avatars avatars = new AvatarFakes.Avatars();
    private static final LocalDateTime CUTOFF = LocalDateTime.of(2026, 10, 1, 0, 0);

    private final RunQueryService runs = mock(RunQueryService.class);
    private final OperationPolicy policy = mock(OperationPolicy.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final Clock clock = Clock.fixed(N.atZone(SEOUL).toInstant(), SEOUL);
    private final AvatarTransactions tx = AvatarFakes.transactions();
    private final BackgroundService service =
            new BackgroundService(
                    avatars, runs, policy, notifications, tx, new BackfillProperties(CUTOFF));

    @BeforeEach
    void defaults() {
        when(policy.landmarkTable()).thenReturn(DEFAULTS);
        when(runs.validDistanceOf(anyLong())).thenReturn(Distance.ofMeters(0));
        when(runs.validDistanceBefore(anyLong(), any())).thenReturn(Distance.ofMeters(0));
    }

    private void total(long memberId, long meters) {
        when(runs.validDistanceOf(memberId)).thenReturn(Distance.ofMeters(meters));
        when(runs.validDistanceBefore(memberId, CUTOFF)).thenReturn(Distance.ofMeters(meters));
    }

    private NotificationRequest onlyNotification() {
        ArgumentCaptor<NotificationRequest> sent =
                ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notifications).notify(sent.capture());
        return sent.getValue();
    }

    @Test
    @DisplayName("TC-6156 랜드마크 표가 비면 빈 목록이고 알림이 없다")
    void emptyTable() {
        when(policy.landmarkTable()).thenReturn(new LandmarkTable(List.of()));
        total(M, 100_000);

        assertThat(service.unlockReached(M, N)).isEmpty();
        verifyNoInteractions(notifications);
    }

    @Test
    @DisplayName("TC-6157 59.999km 없음 / 60.000km·60.001km 인천(정각 포함), 크루 단계만 넘으면 해금하지 않는다")
    void distanceBoundary_andCrewStageExcluded() {
        total(10, 59_999);
        total(11, 60_000);
        total(12, 60_001);
        assertThat(service.unlockReached(10, N)).isEmpty();
        assertThat(service.unlockReached(11, N))
                .extracting(UnlockedBackground::landmarkKey)
                .containsExactly("INCHEON");
        assertThat(service.unlockReached(12, N))
                .extracting(UnlockedBackground::landmarkKey)
                .containsExactly("INCHEON");

        List<Landmark> crewOnly =
                DEFAULTS.landmarks().stream()
                        .filter(l -> l.stage() == LandmarkStage.CREW_TOTAL)
                        .toList();
        when(policy.landmarkTable()).thenReturn(new LandmarkTable(crewOnly));
        total(13, 100_000_000);
        assertThat(service.unlockReached(13, N)).isEmpty();
    }

    @Test
    @DisplayName("TC-6158 한 번에 셋을 넘으면 셋을 해금하고 알림 한 건에 이름을 거리 순으로 모두 적는다, TC-6159 다시는 0")
    void unlocksSeveral_oneNotification_thenIdempotent() {
        total(M, 290_000);

        List<UnlockedBackground> added = service.unlockReached(M, N);

        assertThat(added)
                .extracting(UnlockedBackground::landmarkKey)
                .containsExactly("INCHEON", "CHUNCHEON", "DAEJEON");
        assertThat(added).allSatisfy(u -> assertThat(u.unlockedAt()).isEqualTo(N));
        NotificationRequest req = onlyNotification();
        assertThat(req.recipientId()).isEqualTo(M);
        assertThat(req.type()).isEqualTo(NotificationType.BACKGROUND_UNLOCKED);
        assertThat(req.content()).isEqualTo("새 배경 3개를 해금했어요: 인천, 춘천, 대전");
        assertThat(req.target().screen()).isEqualTo(Screen.AVATAR_BACKGROUND);

        assertThat(service.unlockReached(M, N.plusMinutes(1))).isEmpty();
        verify(notifications, times(1)).notify(any());
        assertThat(avatars.findOrCreate(M).unlockedBackgrounds()).hasSize(3);
    }

    @Test
    @DisplayName("TC-6160 ③ 표에서 빠진 해금 배경은 목록 끝에 해금 상태로 남는다(해금은 취소하지 않는다)")
    void removedFromTable_staysUnlockedAtEnd() {
        total(M, 160_000);
        service.unlockReached(M, N);
        List<Landmark> withoutChuncheon =
                DEFAULTS.landmarks().stream().filter(l -> !l.key().equals("CHUNCHEON")).toList();
        when(policy.landmarkTable()).thenReturn(new LandmarkTable(withoutChuncheon));

        BackgroundsView view = service.backgrounds(M, N.plusDays(1));

        assertThat(view.backgrounds()).hasSize(22);
        BackgroundsView.Item last = view.backgrounds().get(21);
        assertThat(last.key()).isEqualTo("CHUNCHEON");
        assertThat(last.unlocked()).isTrue();
        assertThat(last.unlockedAt()).isEqualTo(N);
        assertThat(view.backgrounds().get(0).key()).isEqualTo("INCHEON");
        assertThat(view.backgrounds().get(0).unlocked()).isTrue();
    }

    @Test
    @DisplayName("TC-6161 choose: 해금한 키 선택 / 잠긴 키·없는 키 BACKGROUND_LOCKED(선택 그대로) / 비우면 기본 배경")
    void choose() {
        total(M, 160_000);
        service.unlockReached(M, N);

        assertThat(service.choose(M, "CHUNCHEON").selectedKey()).isEqualTo("CHUNCHEON");
        for (String locked : List.of("BUSAN", "NO_SUCH")) {
            assertThatThrownBy(() -> service.choose(M, locked))
                    .isInstanceOf(BusinessRuleException.class)
                    .extracting(e -> ((BusinessRuleException) e).reason())
                    .isEqualTo(ReasonCode.BACKGROUND_LOCKED);
        }
        assertThat(service.backgrounds(M, N).selectedKey()).isEqualTo("CHUNCHEON");
        assertThat(service.useDefault(M).selectedKey()).isNull();
    }

    @Test
    @DisplayName("TC-6162 배포 전에 넘은 랜드마크는 조회 때 알림 없이 채우고 목록은 개인 랜드마크 22개 표 순서다")
    void backgrounds_backfillsWithoutNotification() {
        total(M, 160_000);

        BackgroundsView view = service.backgrounds(M, N);

        verifyNoInteractions(notifications);
        assertThat(view.selectedKey()).isNull();
        assertThat(view.backgrounds())
                .extracting(BackgroundsView.Item::key)
                .containsExactlyElementsOf(
                        DEFAULTS.landmarks().stream()
                                .filter(l -> l.stage() != LandmarkStage.CREW_TOTAL)
                                .map(Landmark::key)
                                .toList());
        assertThat(view.backgrounds().stream().filter(BackgroundsView.Item::unlocked))
                .extracting(BackgroundsView.Item::key, BackgroundsView.Item::unlockedAt)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("INCHEON", N),
                        org.assertj.core.groups.Tuple.tuple("CHUNCHEON", N));
        assertThat(view.backgrounds().get(2).unlockedAt()).isNull();
        assertThat(view.backgrounds().get(2).distance()).isEqualTo(Distance.ofKm(280));
        assertThat(view.backgrounds().get(2).stage()).isEqualTo("DOMESTIC");
        assertThat(view.backgrounds().get(2).name()).isEqualTo("대전");
    }

    @Test
    @DisplayName("TC-6162 AvatarService.view도 알림 없이 채우고 backgroundKey를 싣는다")
    void avatarView_backfills() {
        MemberQueryService members = mock(MemberQueryService.class);
        RewardService rewards = mock(RewardService.class);
        when(policy.features()).thenReturn(new FeatureToggles(true, true));
        when(policy.selfieLimit()).thenReturn(new SelfieLimit(1));
        when(members.hasConsent(anyLong(), any(ConsentItem.class))).thenReturn(true);
        AvatarService avatarService =
                new AvatarService(avatars, rewards, policy, service, tx, clock);
        total(M, 160_000);

        assertThat(avatarService.view(M).backgroundKey()).isNull();
        assertThat(avatars.findOrCreate(M).unlockedBackgrounds()).hasSize(2);
        service.choose(M, "INCHEON");
        assertThat(avatarService.view(M).backgroundKey()).isEqualTo("INCHEON");
        assertThat(new AvatarQuery(avatars).backgroundKeyOf(M)).contains("INCHEON");
        verifyNoInteractions(notifications);
    }

    @Test
    @DisplayName("리뷰 H1: 해금 행이 이미 있는 회원은 조회가 채우지 않아 방금 넘은 랜드마크의 알림을 리스너가 낸다")
    void backfill_onlyFirstFill_keepsNewUnlockNotification() {
        total(M, 60_000);
        service.unlockReached(M, N);
        total(M, 160_000);

        assertThat(service.backfillReached(M, N)).isEmpty();
        assertThat(service.backgrounds(M, N).backgrounds().get(1).unlocked()).isFalse();

        assertThat(service.unlockReached(M, N))
                .extracting(UnlockedBackground::landmarkKey)
                .containsExactly("CHUNCHEON");
        verify(notifications, times(2)).notify(any());
    }

    @Test
    @DisplayName("BE5-A-4·D-176: 조회 채우기는 배포 전 거리만 셈해 방금 넘은 첫 랜드마크 알림을 리스너가 낸다")
    void backfill_countsOnlyDistanceBeforeCutoff_keepsFirstNotification() {
        when(runs.validDistanceOf(M)).thenReturn(Distance.ofMeters(60_000));
        when(runs.validDistanceBefore(M, CUTOFF)).thenReturn(Distance.ofMeters(10_000));

        assertThat(service.backfillReached(M, N)).isEmpty();
        assertThat(service.backgrounds(M, N).backgrounds().get(0).unlocked()).isFalse();
        verifyNoInteractions(notifications);

        assertThat(service.unlockReached(M, N))
                .extracting(UnlockedBackground::landmarkKey)
                .containsExactly("INCHEON");
        verify(notifications).notify(any());
    }

    @Test
    @DisplayName("조회 때 채우기가 실패해도 목록은 돌려준다(운영 로그, 보정 배치가 채운다)")
    void backgrounds_fillFailureIsContained() {
        when(runs.validDistanceBefore(M, CUTOFF)).thenThrow(new IllegalStateException("db down"));

        BackgroundsView view = service.backgrounds(M, N);

        assertThat(view.backgrounds()).hasSize(22).noneMatch(BackgroundsView.Item::unlocked);
    }

    @Test
    @DisplayName("TC-6163 ① 보정: 최근 2일 저장 회원마다 unlockReached, 한 회원 실패는 로그 뒤 다음, 처리한 회원 수를 돌려준다")
    void reconcile() {
        when(runs.memberIdsSavedBetween(LocalDateTime.of(2026, 10, 20, 0, 0), N))
                .thenReturn(Set.of(3L, 4L));
        total(3, 290_000);
        when(runs.validDistanceOf(4)).thenThrow(new IllegalStateException("db down"));

        assertThat(service.reconcile(N)).isEqualTo(1);

        assertThat(avatars.findOrCreate(3).unlockedBackgrounds()).hasSize(3);
        assertThat(onlyNotification().content()).isEqualTo("새 배경 3개를 해금했어요: 인천, 춘천, 대전");
        assertThat(service.reconcile(N)).isEqualTo(1);
        verify(notifications, times(1)).notify(any());
    }

    @Test
    @DisplayName("TC-6163 ② 리스너: 유효 기록만 unlockReached, 무효 기록은 0회, 실패는 삼킨다")
    void listener() {
        BackgroundService background = mock(BackgroundService.class);
        BackgroundUnlockListener listener = new BackgroundUnlockListener(background, clock);

        listener.onRunSaved(saved(false));
        verify(background, never()).unlockReached(anyLong(), any());

        when(background.unlockReached(M, N)).thenThrow(new IllegalStateException("db down"));
        listener.onRunSaved(saved(true));
        verify(background).unlockReached(M, N);
    }

    private static RunRecordSaved saved(boolean valid) {
        return new RunRecordSaved(
                "r1",
                M,
                N.minusHours(1),
                Distance.ofMeters(5_000),
                Duration.ofMinutes(30),
                valid,
                valid ? 40.0 : null,
                RunMode.FREE,
                null,
                null,
                true,
                valid ? 1 : 0,
                N);
    }

    // W8 남은 거리(S15P31A207-299, contract-W8.md 2.2)

    @Test
    @DisplayName("목록에 내 누적 거리와 배경마다 남은 거리를 싣는다: 해금했으면 0, 잠겼으면 기준 − 누적")
    void remainingDistance() {
        total(M, 287_000);
        service.unlockReached(M, N);

        BackgroundsView view = service.backgrounds(M, N);

        assertThat(view.total()).isEqualTo(Distance.ofMeters(287_000));
        BackgroundsView.Item daejeon = view.backgrounds().get(2);
        BackgroundsView.Item jeonju = view.backgrounds().get(3);
        assertThat(daejeon.key()).isEqualTo("DAEJEON");
        assertThat(daejeon.unlocked()).isTrue();
        assertThat(daejeon.unlockedAt()).isEqualTo(N);
        assertThat(daejeon.remaining()).isEqualTo(Distance.ZERO);
        assertThat(jeonju.key()).isEqualTo("JEONJU");
        assertThat(jeonju.unlocked()).isFalse();
        assertThat(jeonju.remaining()).isEqualTo(Distance.ofMeters(113_000));
        assertThat(service.useDefault(M).total()).isEqualTo(Distance.ofMeters(287_000));
    }

    @Test
    @DisplayName("누적 거리가 기준을 넘었는데 아직 해금 전이면 남은 거리는 0이다(음수 없음)")
    void remainingDistance_neverNegative() {
        when(runs.validDistanceOf(M)).thenReturn(Distance.ofMeters(500_000));

        BackgroundsView view = service.useDefault(M);

        assertThat(view.backgrounds().get(3).unlocked()).isFalse();
        assertThat(view.backgrounds().get(3).remaining()).isEqualTo(Distance.ZERO);
    }
}
