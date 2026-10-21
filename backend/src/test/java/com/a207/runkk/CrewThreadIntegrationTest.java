package com.a207.runkk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.a207.runkk.avatar.AppearanceService;
import com.a207.runkk.avatar.AvatarQuery;
import com.a207.runkk.avatar.AvatarService;
import com.a207.runkk.avatar.AvatarWithdrawalListener;
import com.a207.runkk.avatar.DefaultCharacter;
import com.a207.runkk.avatar.EquipChange;
import com.a207.runkk.avatar.FacePart;
import com.a207.runkk.avatar.FacePartImageStore;
import com.a207.runkk.avatar.SdFacePartImages;
import com.a207.runkk.avatar.SdFaceParts;
import com.a207.runkk.bootstrap.RunkkApplication;
import com.a207.runkk.common.DisplayName;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.ItemSlot;
import com.a207.runkk.common.PersonalityTag;
import com.a207.runkk.common.ProgressReport;
import com.a207.runkk.common.StandardDistance;
import com.a207.runkk.crew.CrewCreateCommand;
import com.a207.runkk.crew.CrewLiveSessionStore;
import com.a207.runkk.crew.CrewMembersView;
import com.a207.runkk.crew.CrewPostService;
import com.a207.runkk.crew.CrewQueryService;
import com.a207.runkk.crew.CrewRunDraft;
import com.a207.runkk.crew.CrewRunLiveService;
import com.a207.runkk.crew.CrewRunResultView;
import com.a207.runkk.crew.CrewRunService;
import com.a207.runkk.crew.CrewService;
import com.a207.runkk.crew.CrewVisibility;
import com.a207.runkk.crew.DecisionResult;
import com.a207.runkk.crew.JoinPolicy;
import com.a207.runkk.crew.JoinRequestStatus;
import com.a207.runkk.crew.JoinResult;
import com.a207.runkk.crew.JoinTarget;
import com.a207.runkk.crew.MembershipService;
import com.a207.runkk.crew.RaceStatusView;
import com.a207.runkk.crew.RelayEntry;
import com.a207.runkk.crew.WeeklyRaceService;
import com.a207.runkk.member.DisclosureLevel;
import com.a207.runkk.member.MemberFixtures;
import com.a207.runkk.member.MemberQueryService;
import com.a207.runkk.member.MemberRepository;
import com.a207.runkk.member.MemberSettingService;
import com.a207.runkk.member.MemberWithdrawn;
import com.a207.runkk.member.SettingChange;
import com.a207.runkk.member.SettingItem;
import com.a207.runkk.profile.ProfileDisclosure;
import com.a207.runkk.profile.ProfileQueryService;
import com.a207.runkk.profile.ProfileView;
import com.a207.runkk.reward.RewardService;
import com.a207.runkk.running.CardKind;
import com.a207.runkk.running.LocationPoint;
import com.a207.runkk.running.RunFixtures;
import com.a207.runkk.running.RunMode;
import com.a207.runkk.running.RunRecordSaved;
import com.a207.runkk.running.RunUpload;
import com.a207.runkk.running.RunUploadCommand;
import com.a207.runkk.running.RunUploadService;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 14장 통합 — 스레드 기반(B4 범위). 실 PostgreSQL·Redis 위에서: T-4 크루런 스레드(SQ-09, 참가자 10명: 대기실 → 출발 → 1초 진행값 중계
 * → 동시 기록 업로드로 러닝 연결·도착 → 종료·순위), T-5 가입 스레드(SQ-08, 동시 승인·동시 자유 가입의 정원 경합), T-9 주간 거리 재합산(수신자 실패를
 * 흉내 낸 뒤 reconcileAll로 복구, 이중 합산 0), T-12 공개 범위(비공개 크루원이 크루원 목록과 프로필 화면(ProfileQueryService)에서 같은
 * 수준, 한 곳 변경이 두 화면에 함께 반영), 리워드 하루 상한 동시 적립(5.3).
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = RunkkApplication.class)
@Import(TestcontainersConfig.class)
@ActiveProfiles("integration")
@org.junit.jupiter.api.DisplayName("크루 스레드 통합(T-4, T-5, T-9, T-12)")
class CrewThreadIntegrationTest {

    private static final int RUNNERS = 10;
    private static final AtomicInteger NAMES = new AtomicInteger();

    @MockitoSpyBean WeeklyRaceService weeklyRaceService;
    @Autowired CrewService crews;
    @Autowired CrewQueryService crewQueries;
    @Autowired MembershipService membership;
    @Autowired CrewPostService posts;
    @Autowired CrewRunService crewRuns;
    @Autowired CrewRunLiveService live;
    @Autowired CrewLiveSessionStore sessions;
    @Autowired RunUploadService uploads;
    @Autowired RewardService rewards;
    @Autowired MemberRepository members;
    @Autowired MemberQueryService memberQueries;
    @Autowired MemberSettingService memberSettings;
    @Autowired TransactionTemplate tx;
    @Autowired AvatarService avatars;
    @Autowired AvatarQuery avatarQuery;
    @Autowired FacePartImageStore faceParts;
    @Autowired AppearanceService appearance;
    @Autowired AvatarWithdrawalListener avatarWithdrawal;
    @Autowired JdbcTemplate jdbc;
    @Autowired StringRedisTemplate redis;
    @Autowired ProfileQueryService profiles;

    @BeforeEach
    void clean() {
        for (String table :
                List.of(
                        "race_contribution",
                        "weekly_race",
                        "crew_run_participation",
                        "crew_post",
                        "join_request",
                        "crew_membership",
                        "crew",
                        "avatar_face_part",
                        "avatar_equipment",
                        "avatar",
                        "face_part_image",
                        "reward_accrual",
                        "owned_item",
                        "reward_wallet",
                        "course_verdict",
                        "runimal_profile",
                        "notification",
                        "personal_best",
                        "personal_record_book",
                        "run_record",
                        "member_consent",
                        "member",
                        "audit_record")) {
            jdbc.update("delete from " + table);
        }
        redis.getRequiredConnectionFactory().getConnection().serverCommands().flushAll();
    }

    private long member() {
        String name = "통합러너" + NAMES.incrementAndGet();
        return tx.execute(
                s ->
                        members.save(MemberFixtures.member(name, LocalDateTime.now().minusDays(30)))
                                .getId());
    }

    private long crew(long leader, JoinPolicy policy) {
        return crews.create(
                        leader,
                        new CrewCreateCommand(
                                "통합크루" + NAMES.incrementAndGet(),
                                "함께",
                                Set.of(PersonalityTag.SOCIAL_RUN),
                                CrewVisibility.PUBLIC,
                                policy,
                                null))
                .crewId();
    }

    private static <T> List<Future<T>> together(List<Callable<T>> work) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(work.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>();
        for (Callable<T> w : work) {
            futures.add(
                    pool.submit(
                            () -> {
                                start.await();
                                return w.call();
                            }));
        }
        start.countDown();
        pool.shutdown();
        return futures;
    }

    private static RunUpload crewRun(long postId, LocalDateTime start, long meters, int seconds) {
        List<LocationPoint> points =
                RunFixtures.straightLine(start, (double) meters / seconds, seconds);
        return new RunUpload(
                UUID.randomUUID().toString(),
                start,
                start.plusSeconds(seconds),
                Distance.ofMeters(meters),
                Duration.ofSeconds(seconds),
                postId == 0 ? RunMode.FREE : RunMode.CREW_RUN,
                postId == 0 ? CardKind.FREE_RUN : CardKind.CREW_RUN,
                null,
                postId == 0 ? null : postId,
                points.size(),
                points,
                List.of(),
                List.of());
    }

    @Test
    void t4_crewRunWithTenRunners() throws Exception {
        long leader = member();
        long crewId = crew(leader, JoinPolicy.FREE);
        List<Long> runners = new ArrayList<>(List.of(leader));
        for (int i = 1; i < RUNNERS; i++) {
            long m = member();
            membership.apply(m, JoinTarget.byCrew(crewId));
            runners.add(m);
        }
        LocalDateTime scheduledAt = LocalDateTime.now().plusMinutes(20).withNano(0);
        long postId =
                posts.writeCrewRun(
                                leader,
                                crewId,
                                new CrewRunDraft(
                                        "10명 크루런", scheduledAt, StandardDistance.KM1, null))
                        .postId();
        for (long m : runners) {
            crewRuns.enterLobby(m, postId);
            crewRuns.toggleReady(m, postId);
        }

        crewRuns.tick(scheduledAt);
        LocalDateTime start = scheduledAt.plusSeconds(3);
        assertThat(sessions.load(postId)).isPresent();

        List<Callable<Boolean>> progress = new ArrayList<>();
        for (int i = 0; i < RUNNERS; i++) {
            long m = runners.get(i);
            long meters = 100L + i * 10;
            progress.add(
                    () -> {
                        live.onProgress(
                                m,
                                postId,
                                new ProgressReport(
                                        Distance.ofMeters(meters), Duration.ofSeconds(60), start),
                                start.plusSeconds(60));
                        return true;
                    });
        }
        for (Future<Boolean> f : together(progress)) {
            assertThat(f.get()).isTrue();
        }
        List<RelayEntry> ranking = sessions.load(postId).orElseThrow().ranking();
        assertThat(ranking).hasSize(RUNNERS);
        assertThat(ranking.get(0).distance()).isEqualTo(Distance.ofMeters(190));

        List<Callable<Long>> uploadsAtOnce = new ArrayList<>();
        for (int i = 0; i < RUNNERS; i++) {
            long m = runners.get(i);
            int seconds = 300 + i * 5;
            uploadsAtOnce.add(
                    () -> {
                        uploads.upload(
                                m, new RunUploadCommand(crewRun(postId, start, 1_000, seconds)));
                        return m;
                    });
        }
        for (Future<Long> f : together(uploadsAtOnce)) {
            f.get();
        }
        crewRuns.tick(start.plusMinutes(15));

        CrewRunResultView result = crewRuns.result(leader, postId);
        assertThat(result.finisherCount()).isEqualTo(RUNNERS);
        assertThat(result.runners())
                .extracting(CrewRunResultView.Line::rank)
                .containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        assertThat(result.runners().get(0).name()).isNotEqualTo(DisplayName.WITHDRAWN);
        assertThat(result.crewContribution()).isEqualTo(Distance.ofMeters(10_000));
        assertThat(result.firstCrewRun()).isTrue();
        assertThat(sessions.load(postId)).isEmpty();
    }

    @Test
    void t5_concurrentApprovalsAndJoins_neverExceedCapacity() throws Exception {
        long leader = member();
        long approvalCrew = crew(leader, JoinPolicy.APPROVAL);
        List<Long> requests = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            requests.add(membership.apply(member(), JoinTarget.byCrew(approvalCrew)).requestId());
        }
        for (int i = 0; i < 8; i++) {
            long m = member();
            long request = membership.apply(m, JoinTarget.byCrew(approvalCrew)).requestId();
            membership.approveJoin(leader, request);
        }
        List<Callable<DecisionResult>> approvals = new ArrayList<>();
        requests.forEach(id -> approvals.add(() -> membership.approveJoin(leader, id)));
        List<JoinRequestStatus> statuses = new ArrayList<>();
        for (Future<DecisionResult> f : together(approvals)) {
            statuses.add(f.get().status());
        }
        assertThat(statuses)
                .containsExactlyInAnyOrder(
                        JoinRequestStatus.APPROVED,
                        JoinRequestStatus.DISSOLVED,
                        JoinRequestStatus.DISSOLVED);
        assertThat(crewQueries.members(leader, approvalCrew).members()).hasSize(10);

        long freeLeader = member();
        long freeCrew = crew(freeLeader, JoinPolicy.FREE);
        for (int i = 0; i < 8; i++) {
            membership.apply(member(), JoinTarget.byCrew(freeCrew));
        }
        List<Callable<Optional<JoinResult>>> joins = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            long m = member();
            joins.add(
                    () -> {
                        try {
                            return Optional.of(membership.apply(m, JoinTarget.byCrew(freeCrew)));
                        } catch (RuntimeException full) {
                            return Optional.empty();
                        }
                    });
        }
        int joined = 0;
        for (Future<Optional<JoinResult>> f : together(joins)) {
            joined += f.get().isPresent() ? 1 : 0;
        }
        assertThat(joined).isEqualTo(1);
        assertThat(crewQueries.members(freeLeader, freeCrew).members()).hasSize(10);
    }

    @Test
    void t9_failedReceiver_isRecoveredByReconcileWithoutDoubleCounting() {
        long leader = member();
        long crewId = crew(leader, JoinPolicy.FREE);
        LocalDateTime start = LocalDateTime.now().plusMinutes(1).withNano(0);
        doThrow(new IllegalStateException("수신자 장애")).when(weeklyRaceService).onRunSaved(any());
        uploads.upload(leader, new RunUploadCommand(crewRun(0, start, 3_000, 1_200)));
        org.mockito.Mockito.reset(weeklyRaceService);
        assertThat(weeklyRaceService.status(leader, crewId, Optional.empty()).total())
                .isEqualTo(Distance.ZERO);

        uploads.upload(leader, new RunUploadCommand(crewRun(0, start.plusMinutes(30), 2_000, 800)));
        assertThat(weeklyRaceService.reconcileAll(LocalDateTime.now())).isEqualTo(1);
        assertThat(weeklyRaceService.reconcileAll(LocalDateTime.now())).isZero();

        RaceStatusView status = weeklyRaceService.status(leader, crewId, Optional.empty());
        assertThat(status.total()).isEqualTo(Distance.ofMeters(5_000));
        assertThat(status.myRate()).isEqualTo(100);
    }

    @Test
    void t12_privateMember_isDisclosedAtTheSameLevelEverywhere() {
        long leader = member();
        long hidden = member();
        long shown = member();
        long crewId = crew(leader, JoinPolicy.FREE);
        membership.apply(hidden, JoinTarget.byCrew(crewId));
        membership.apply(shown, JoinTarget.byCrew(crewId));
        memberSettings.changeSetting(
                hidden, new SettingChange(SettingItem.PROFILE_VISIBILITY, "PRIVATE", null));
        memberSettings.changeSetting(
                shown, new SettingChange(SettingItem.PROFILE_VISIBILITY, "PUBLIC", null));

        CrewMembersView view = crewQueries.members(leader, crewId);
        Map<Long, DisclosureLevel> levels =
                memberQueries.disclosureOf(leader, List.of(hidden, shown, leader));

        assertThat(levels.get(hidden)).isEqualTo(DisclosureLevel.PRIVATE);
        assertThat(view.disclosedMemberIds())
                .containsExactlyInAnyOrder(shown, leader)
                .allMatch(id -> levels.get(id).showsProfile());
        CrewMembersView.Line hiddenLine =
                view.members().stream()
                        .filter(l -> l.memberId() == hidden)
                        .findFirst()
                        .orElseThrow();
        assertThat(hiddenLine.crewTags()).isNull();
        assertThat(hiddenLine.tier()).isNull();
        assertThat(hiddenLine.name()).isNotNull();

        // B5: 프로필 화면(ProfileQueryService)도 같은 DisclosureLevel을 쓴다(D-52)
        for (long id : List.of(hidden, shown, leader)) {
            ProfileView profile = profiles.view(leader, id);
            assertThat(profile.disclosure().name()).isEqualTo(levels.get(id).name());
            assertThat(view.disclosedMemberIds().contains(id))
                    .isEqualTo(levels.get(id).showsProfile());
        }
        ProfileView hiddenProfile = profiles.view(leader, hidden);
        assertThat(hiddenProfile.tags()).isEmpty();
        assertThat(hiddenProfile.runimalType()).isNull();
        assertThat(hiddenProfile.crew().crewId()).isEqualTo(crewId);

        // 공개 범위 한 곳 변경이 두 화면에 함께 반영된다
        memberSettings.changeSetting(
                hidden, new SettingChange(SettingItem.PROFILE_VISIBILITY, "PUBLIC", null));
        assertThat(crewQueries.members(leader, crewId).disclosedMemberIds()).contains(hidden);
        assertThat(profiles.view(leader, hidden).disclosure()).isEqualTo(ProfileDisclosure.PUBLIC);
    }

    @Test
    void rewardDailyCap_holdsUnderConcurrentAccruals() throws Exception {
        long m = member();
        List<Callable<Boolean>> accruals = new ArrayList<>();
        for (int i = 0; i < RUNNERS; i++) {
            String recordId = UUID.randomUUID().toString();
            accruals.add(
                    () -> {
                        rewards.onRunSaved(
                                new RunRecordSaved(
                                        recordId,
                                        m,
                                        LocalDateTime.now(),
                                        Distance.ofKm(5),
                                        Duration.ofMinutes(30),
                                        true,
                                        40.0,
                                        RunMode.FREE,
                                        null,
                                        null,
                                        true,
                                        1,
                                        LocalDateTime.now()));
                        return true;
                    });
        }
        for (Future<Boolean> f : together(accruals)) {
            f.get();
        }
        assertThat(rewards.balanceOf(m)).isEqualTo(200);
    }

    @Test
    void avatarAndFacePartImages_persistOnPostgres() {
        long m = member();
        avatars.chooseDefault(m, DefaultCharacter.CAT);
        assertThat(avatarQuery.faceKey(m)).contains("default:CAT");
        SdFaceParts parts =
                tx.execute(
                        s -> {
                            Map<FacePart, byte[]> images = new EnumMap<>(FacePart.class);
                            for (FacePart part : FacePart.values()) {
                                images.put(part, new byte[] {(byte) 0x89, 'P', 'N', 'G', 1});
                            }
                            return faceParts.save(m, new SdFacePartImages(images));
                        });
        assertThat(parts.setKey()).startsWith("face/" + m + "/");
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from face_part_image where member_id = ?",
                                Long.class,
                                m))
                .isEqualTo(5);
        assertThat(avatars.equip(m, List.of(EquipChange.wear(ItemSlot.HAT, 77))).rejected())
                .containsKey(ItemSlot.HAT);
        assertThat(appearance.refreshAll(LocalDate.now())).isZero();
        avatarWithdrawal.onMemberWithdrawn(new MemberWithdrawn(m, LocalDateTime.now()));
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from face_part_image where member_id = ?",
                                Long.class,
                                m))
                .isZero();
        assertThat(avatarQuery.faceKey(m)).isEmpty();
    }
}
