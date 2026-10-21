package com.a207.runkk.runimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.DateRange;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.GeoPoint;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.common.TagOwner;
import com.a207.runkk.common.TagSet;
import com.a207.runkk.member.MemberQueryService;
import com.a207.runkk.member.MemberWithdrawn;
import com.a207.runkk.policy.CourseSimilarityRule;
import com.a207.runkk.policy.OperationPolicy;
import com.a207.runkk.running.RunMode;
import com.a207.runkk.running.RunQueryService;
import com.a207.runkk.running.RunRecordSaved;
import com.a207.runkk.running.RunSampleRow;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

/** runimal 응용 서비스(14장 단위, SQ-10): 인메모리 저장소·가짜 조회 서비스·고정 시계. */
@org.junit.jupiter.api.DisplayName("runimal 응용 서비스")
class RunimalServicesTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 21, 8, 0);
    private static final long MEMBER = 5L;

    /** 인메모리 판정 상태 저장소. */
    static final class Profiles implements RunimalProfileRepository {
        final Map<Long, RunimalProfile> rows = new HashMap<>();

        @Override
        public RunimalProfile findOrCreate(long memberId) {
            return rows.getOrDefault(memberId, RunimalProfile.create(memberId));
        }

        @Override
        public void save(RunimalProfile profile) {
            rows.put(profile.getMemberId(), profile);
        }

        @Override
        public Map<Long, RunimalType> typesOf(Collection<Long> memberIds) {
            Map<Long, RunimalType> types = new HashMap<>();
            memberIds.forEach(
                    id -> {
                        if (rows.containsKey(id)) {
                            rows.get(id).currentType().ifPresent(t -> types.put(id, t));
                        }
                    });
            return types;
        }

        @Override
        public void deleteByMember(long memberId) {
            rows.remove(memberId);
        }
    }

    /** 인메모리 코스 판정 저장소. */
    static final class Verdicts implements CourseVerdictRepository {
        final Map<String, CourseVerdict> rows = new HashMap<>();

        @Override
        public void save(CourseVerdict verdict) {
            rows.put(verdict.getRecordId(), verdict);
        }

        @Override
        public Map<String, Boolean> findByRecords(Collection<String> recordIds) {
            Map<String, Boolean> found = new HashMap<>();
            recordIds.forEach(
                    id -> {
                        if (rows.containsKey(id)) {
                            found.put(id, rows.get(id).isSameCourse());
                        }
                    });
            return found;
        }

        @Override
        public void deleteByMember(long memberId) {
            rows.values().removeIf(v -> v.getMemberId() == memberId);
        }
    }

    private final Profiles profiles = new Profiles();
    private final Verdicts verdicts = new Verdicts();
    private final RunQueryService runs = mock(RunQueryService.class);
    private final MemberQueryService members = mock(MemberQueryService.class);
    private final OperationPolicy policy = mock(OperationPolicy.class);
    private final Clock clock = Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL);
    private final List<RunSampleRow> history = new ArrayList<>();
    private RunimalService service;

    @BeforeEach
    void wire() {
        when(policy.runimalRule()).thenReturn(RunimalJudgeTest.RULE);
        when(policy.courseSimilarityRule()).thenReturn(new CourseSimilarityRule(0.8, 50, 30));
        when(members.tagsOf(MEMBER)).thenReturn(TagSet.empty(TagOwner.RUNNER));
        when(runs.runSamples(eq(MEMBER), anyInt())).thenAnswer(inv -> List.copyOf(history));
        when(runs.runSamples(eq(MEMBER), any(DateRange.class)))
                .thenAnswer(inv -> List.copyOf(history));
        when(runs.routePoints(any())).thenReturn(List.of());
        CourseSimilarityService courses =
                new CourseSimilarityService(
                        runs, verdicts, new CourseSimilarityJudge(), policy, clock);
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        ObjectProvider<CrewRunParticipationQuery> none =
                beans.getBeanProvider(CrewRunParticipationQuery.class);
        RunimalJudge judge =
                new RunimalJudge(
                        List.of(
                                new CompanionAxisEvaluator(),
                                new DistanceAxisEvaluator(),
                                new CourseAxisEvaluator(),
                                new HabitAxisEvaluator()));
        service =
                new RunimalService(
                        profiles,
                        courses,
                        judge,
                        new RunSampleReader(runs, verdicts, none),
                        members,
                        policy,
                        clock);
    }

    private RunRecordSaved saved(int ordinal, boolean valid, boolean hasRoute) {
        String id = "r" + ordinal;
        history.add(
                0,
                new RunSampleRow(id, NOW.minusDays(ordinal).withHour(7), Distance.ofMeters(2_000)));
        return new RunRecordSaved(
                id,
                MEMBER,
                NOW,
                Distance.ofMeters(2_000),
                Duration.ofMinutes(12),
                valid,
                valid ? 30.0 : null,
                RunMode.FREE,
                null,
                null,
                hasRoute,
                valid ? ordinal : 0,
                NOW);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("3회째 임시 유형, 4~9회 다시 판정 안 함, 10회째 확정, 무효 기록은 무시")
    void provisionalThenConfirmed() {
        service.onRunSaved(saved(1, true, false));
        service.onRunSaved(saved(2, true, false));
        assertThat(service.status(MEMBER).card()).isNull();
        service.onRunSaved(saved(3, true, false));
        RunimalStatusView status = service.status(MEMBER);
        assertThat(status.provisionalType()).isEqualTo(RunimalType.MORNING_SPARROW);
        assertThat(status.remainingRuns()).isEqualTo(7);
        service.onRunSaved(saved(0, false, false));
        for (int i = 4; i <= 10; i++) {
            service.onRunSaved(saved(i, true, false));
        }
        status = service.status(MEMBER);
        assertThat(status.confirmedType()).isNotNull();
        assertThat(status.card().provisional()).isFalse();
        assertThat(status.allTypes()).hasSize(16);
        assertThat(service.shareCard(MEMBER).type()).isEqualTo(status.confirmedType());
    }

    @Test
    @org.junit.jupiter.api.DisplayName("T-10 절반: 3회째 처리가 빠져도 4회째에 임시 유형이 나온다(D-40)")
    void skippedThird_isRecoveredOnFourth() {
        service.onRunSaved(saved(1, true, false));
        service.onRunSaved(saved(2, true, false));
        saved(3, true, false); // 3회째 수신자 실패를 흉내: 사건을 넘기지 않는다
        service.onRunSaved(saved(4, true, false));
        assertThat(service.status(MEMBER).provisionalType()).isNotNull();
    }

    @Test
    @org.junit.jupiter.api.DisplayName("유형이 없으면 공유 카드는 NO_TYPE, RunimalQuery는 유형 있는 회원만")
    void noType_andQuery() {
        assertThatThrownBy(() -> service.shareCard(MEMBER))
                .extracting(e -> ((BusinessRuleException) e).reason())
                .isEqualTo(ReasonCode.NO_TYPE);
        for (int i = 1; i <= 3; i++) {
            service.onRunSaved(saved(i, true, false));
        }
        RunimalQuery query = new RunimalQuery(profiles);
        assertThat(query.typesOf(List.of(MEMBER, 99L))).containsOnlyKeys(MEMBER);
        assertThat(query.typesOf(List.of())).isEmpty();
    }

    @Test
    @org.junit.jupiter.api.DisplayName("경로가 있으면 코스 판정을 남기고, 비교할 경로가 없으면 남기지 않는다")
    void courseVerdict_savedOnlyWithPreviousRoutes() {
        List<GeoPoint> line = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            line.add(new GeoPoint(37.5 + i * 0.0001, 127.0));
        }
        when(runs.routePoints("r1")).thenReturn(line);
        service.onRunSaved(saved(1, true, true));
        assertThat(verdicts.rows).isEmpty();

        when(runs.routePoints("r2")).thenReturn(line);
        service.onRunSaved(saved(2, true, true));
        assertThat(verdicts.rows.get("r2").isSameCourse()).isTrue();
        verify(runs, never()).routePoints("r3");
    }

    @Test
    @org.junit.jupiter.api.DisplayName("탈퇴하면 판정 상태·코스 판정을 지운다")
    void withdrawal_deletes() {
        service.onRunSaved(saved(1, true, false));
        verdicts.save(new CourseVerdict("x", MEMBER, true, NOW));
        new RunimalWithdrawalListener(profiles, verdicts)
                .onMemberWithdrawn(new MemberWithdrawn(MEMBER, NOW));
        assertThat(profiles.rows).isEmpty();
        assertThat(verdicts.rows).isEmpty();
    }

    @Test
    @org.junit.jupiter.api.DisplayName("크루런 참가 조회가 있으면 동행 축 표본에 쓴다")
    void participationQuery_isUsedWhenPresent() {
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        CrewRunParticipationQuery crew = (member, range) -> Set.of("r1");
        beans.addBean("crew", crew);
        history.add(new RunSampleRow("r1", NOW, Distance.ofMeters(1_000)));
        RunSampleReader reader =
                new RunSampleReader(
                        runs, verdicts, beans.getBeanProvider(CrewRunParticipationQuery.class));
        RunSample sample = reader.read(MEMBER, NOW.toLocalDate());
        assertThat(sample.lastMonth()).allMatch(RunSample.SampleRun::crewRunLinked);
        verify(members, never()).tagsOf(anyLong());
    }
}
