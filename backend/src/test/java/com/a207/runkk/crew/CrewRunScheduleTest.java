package com.a207.runkk.crew;

import static com.a207.runkk.crew.CrewFixtures.LEADER;
import static com.a207.runkk.crew.CrewFixtures.NOW;
import static com.a207.runkk.crew.CrewFixtures.RULE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.PermissionDeniedException;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.common.StandardDistance;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** W6 크루 일정(S15P31A207-297): 제목·내용 분리, 거리 선택지, 15일·5분, 수정, 반복 회차, 참석 두 상태. */
@DisplayName("크루 일정 규칙(W6)")
class CrewRunScheduleTest {

    /** 2026-10-24(토) 07:00. NOW는 2026-10-21(수) 09:00. */
    private static final LocalDateTime SAT = LocalDateTime.of(2026, 10, 24, 7, 0);

    private static final long OTHER = 7;

    private static ReasonCode reasonOf(Throwable e) {
        return ((BusinessRuleException) e).reason();
    }

    private static CrewRunDraft draft(String title, String body, StandardDistance distance) {
        return new CrewRunDraft(title, body, SAT, distance, null, CrewRunRepeat.NONE);
    }

    private static CrewRunPost post(StandardDistance distance) {
        return new CrewRunPost(9, LEADER, draft("토요일 한강", "8번 출구", distance), NOW);
    }

    @Test
    @DisplayName("참석 취소는 응답을 거둔다(작성자도), 마감 뒤는 VOTE_CLOSED, 응답이 없으면 그대로")
    void withdraw() {
        CrewRunPost post = post(StandardDistance.KM5);
        post.answer(OTHER, AttendanceAnswer.GOING, NOW);
        AttendanceView view = post.withdraw(OTHER, NOW);
        assertThat(view.myAnswer()).isNull();
        assertThat(view.goingCount()).isEqualTo(1);
        assertThat(post.participationOf(OTHER)).isEmpty();
        assertThat(post.withdraw(OTHER, NOW).goingCount()).isEqualTo(1);
        assertThat(post.withdraw(LEADER, NOW).goingCount()).isZero();
        assertThatThrownBy(() -> post.withdraw(OTHER, SAT))
                .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(ReasonCode.VOTE_CLOSED));
    }

    @Test
    @DisplayName("거리 선택지는 제한 없음·3·5·10km·하프, 1km는 새로 쓸 수 없다(C4)")
    void distanceOptions() {
        assertThat(Arrays.stream(StandardDistance.values()).filter(StandardDistance::isCrewRun))
                .containsExactly(
                        StandardDistance.KM3,
                        StandardDistance.KM5,
                        StandardDistance.KM10,
                        StandardDistance.HALF);
        assertThat(post(null).distance()).isNull();
        assertThat(post(StandardDistance.HALF).distance()).isEqualTo(StandardDistance.HALF);
        assertThatThrownBy(() -> post(StandardDistance.KM1))
                .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(ReasonCode.CREW_RUN_INVALID));
        assertThatThrownBy(() -> post(StandardDistance.FULL))
                .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(ReasonCode.CREW_RUN_INVALID));
    }

    @Test
    @DisplayName("제한 없음 크루런은 하프 거리 기준 시간이 지나면 닫힌다")
    void unlimitedClosesByHalfBasis() {
        CrewRunPost post = post(null);
        post.answer(OTHER, AttendanceAnswer.GOING, NOW);
        post.enterLobby(LEADER, SAT.minusMinutes(10), RULE);
        post.enterLobby(OTHER, SAT.minusMinutes(10), RULE);
        post.toggleReady(LEADER);
        post.toggleReady(OTHER);
        post.depart(SAT, RULE);
        LocalDateTime departed = post.departedAt().orElseThrow();
        LocalDateTime closes = RULE.closesAt(departed, StandardDistance.HALF);
        assertThat(post.shouldClose(closes.minusSeconds(1), RULE)).isFalse();
        assertThat(post.shouldClose(closes, RULE)).isTrue();
    }

    @Test
    @DisplayName("제목은 공백 제외 1~30자(코드 포인트), 내용은 200자까지이고 따로 저장한다")
    void titleAndBody() {
        CrewRunPost post = post(StandardDistance.KM5);
        assertThat(post.title()).isEqualTo("토요일 한강");
        assertThat(post.body()).isEqualTo("8번 출구");
        assertThat(new CrewRunPost(9, LEADER, draft("가".repeat(30), null, null), NOW).body())
                .isEmpty();
        for (String bad : new String[] {null, " ", "가".repeat(31)}) {
            assertThatThrownBy(() -> new CrewRunPost(9, LEADER, draft(bad, "", null), NOW))
                    .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(ReasonCode.CREW_RUN_INVALID));
        }
        assertThatCode(() -> new CrewRunPost(9, LEADER, draft("😀".repeat(30), "", null), NOW))
                .doesNotThrowAnyException();
        assertThatThrownBy(
                        () -> new CrewRunPost(9, LEADER, draft("제목", "가".repeat(201), null), NOW))
                .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(ReasonCode.BODY_TOO_LONG));
    }

    @Test
    @DisplayName("옛 4칸 입력은 본문 첫 줄을 제목으로 삼는다(호환)")
    void legacyDraftDerivesTitle() {
        CrewRunDraft legacy = new CrewRunDraft("첫 줄\n둘째 줄", SAT, StandardDistance.KM5, null);
        assertThat(legacy.title()).isEqualTo("첫 줄");
        assertThat(legacy.repeat()).isEqualTo(CrewRunRepeat.NONE);
        assertThat(new CrewRunDraft(null, SAT, StandardDistance.KM5, null).title())
                .isEqualTo("크루런");
    }

    @Test
    @DisplayName("일시 선택 규칙: 오늘~14일 뒤, 5분 단위, 초 0, 지금 뒤. 아니면 CREW_RUN_INVALID(F-12)")
    void slotRule() {
        assertThatCode(() -> CrewRunSlot.requireSelectable(NOW.plusMinutes(5), NOW))
                .doesNotThrowAnyException();
        assertThatCode(
                        () ->
                                CrewRunSlot.requireSelectable(
                                        NOW.toLocalDate().plusDays(14).atTime(23, 55), NOW))
                .doesNotThrowAnyException();
        for (LocalDateTime bad :
                new LocalDateTime[] {
                    NOW,
                    NOW.minusMinutes(5),
                    NOW.plusMinutes(7),
                    NOW.plusMinutes(5).plusSeconds(1),
                    NOW.toLocalDate().plusDays(15).atTime(0, 0),
                    null
                }) {
            assertThatThrownBy(() -> CrewRunSlot.requireSelectable(bad, NOW))
                    .as(String.valueOf(bad))
                    .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(ReasonCode.CREW_RUN_INVALID));
        }
    }

    @Test
    @DisplayName("수정: 작성자만, VOTING이고 일시 전, 제목·내용·거리만 바뀌고 기존 1km는 그대로 둘 수 있다")
    void edit() {
        CrewRunPost post = post(StandardDistance.KM5);
        post.edit(LEADER, new CrewRunEdit("새 제목", "새 내용", null, CrewRunRepeat.NONE), NOW);
        assertThat(post.title()).isEqualTo("새 제목");
        assertThat(post.body()).isEqualTo("새 내용");
        assertThat(post.distance()).isNull();
        assertThat(post.scheduledAt()).isEqualTo(SAT);
        assertThat(post.canEdit(LEADER, NOW)).isTrue();
        assertThat(post.canEdit(OTHER, NOW)).isFalse();

        assertThatThrownBy(
                        () ->
                                post.edit(
                                        OTHER,
                                        new CrewRunEdit("남의 글", "", null, CrewRunRepeat.NONE),
                                        NOW))
                .isInstanceOf(PermissionDeniedException.class);
        assertThatThrownBy(
                        () ->
                                post.edit(
                                        LEADER,
                                        new CrewRunEdit(
                                                "1km",
                                                "",
                                                StandardDistance.KM1,
                                                CrewRunRepeat.NONE),
                                        NOW))
                .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(ReasonCode.CREW_RUN_INVALID));
        assertThatThrownBy(
                        () ->
                                post.edit(
                                        LEADER,
                                        new CrewRunEdit(" ", "", null, CrewRunRepeat.NONE),
                                        NOW))
                .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(ReasonCode.CREW_RUN_INVALID));
        assertThatThrownBy(
                        () ->
                                post.edit(
                                        LEADER,
                                        new CrewRunEdit("늦음", "", null, CrewRunRepeat.NONE),
                                        SAT))
                .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(ReasonCode.ALREADY_STARTED));
        assertThat(post.canEdit(LEADER, SAT)).isFalse();

        CrewRunPost lobby = post(StandardDistance.KM5);
        lobby.openLobby(SAT.minusMinutes(30), RULE);
        assertThatThrownBy(
                        () ->
                                lobby.edit(
                                        LEADER,
                                        new CrewRunEdit("대기실", "", null, CrewRunRepeat.NONE),
                                        SAT.minusMinutes(20)))
                .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(ReasonCode.ALREADY_STARTED));
    }

    @Test
    @DisplayName("기존 1km 글은 거리를 바꾸지 않으면 1km로 수정할 수 있다")
    void editKeepsLegacyDistance() {
        CrewRunPost legacy = post(StandardDistance.KM5);
        ReflectionTestUtils.setField(legacy, "distance", StandardDistance.KM1); // V56 전 저장된 글
        legacy.edit(
                LEADER, new CrewRunEdit("제목", "", StandardDistance.KM1, CrewRunRepeat.NONE), NOW);
        assertThat(legacy.distance()).isEqualTo(StandardDistance.KM1);
    }

    @Test
    @DisplayName("다음 회차는 최신 회차의 제목·내용·거리를 복사하고 작성자는 참석, 상태 VOTING")
    void nextOccurrenceCopiesLatest() {
        CrewRunPost latest = post(StandardDistance.HALF);
        latest.answer(OTHER, AttendanceAnswer.GOING, NOW);
        CrewRunPost next = latest.nextOccurrence(5L, SAT.plusDays(7), SAT);
        assertThat(next.title()).isEqualTo("토요일 한강");
        assertThat(next.body()).isEqualTo("8번 출구");
        assertThat(next.distance()).isEqualTo(StandardDistance.HALF);
        assertThat(next.scheduledAt()).isEqualTo(SAT.plusDays(7));
        assertThat(next.seriesId()).isEqualTo(5L);
        assertThat(next.status()).isEqualTo(CrewRunStatus.VOTING);
        assertThat(next.participantsToRemind()).containsExactly(LEADER);
    }

    @Test
    @DisplayName("반복(D-성-26): 일요일 오전 9시에 다음 주(월~일)에 드는 +7일 회차가 차례, 만든 뒤·끝난 뒤는 아니다, 놓친 주는 건너뛴다")
    void series() {
        LocalDateTime sundayBefore = LocalDateTime.of(2026, 10, 18, 9, 0);
        LocalDateTime sunday = LocalDateTime.of(2026, 10, 25, 9, 0);
        CrewRunPost first = post(StandardDistance.KM5);
        CrewRunSeries series = CrewRunSeries.start(first, 41L, NOW);
        assertThat(series.isActive()).isTrue();
        assertThat(series.dayOfWeek()).isEqualTo(DayOfWeek.SATURDAY);
        assertThat(series.isLatest(41L)).isTrue();
        assertThat(CrewRunSeries.windowEnd(sunday)).isEqualTo(LocalDateTime.of(2026, 11, 2, 0, 0));
        assertThat(series.isDue(sundayBefore)).as("10.31은 다음다음 주").isFalse();
        assertThat(series.isDue(sunday)).isTrue();
        assertThat(series.nextSlot(sunday)).isEqualTo(SAT.plusDays(7));
        assertThat(series.nextSlot(SAT.plusDays(20))).as("놓친 주 건너뜀").isEqualTo(SAT.plusDays(21));

        series.advance(42L, SAT.plusDays(7));
        assertThat(series.isLatest(41L)).isFalse();
        assertThat(series.isLatest(42L)).isTrue();
        assertThat(series.isDue(sunday)).as("이미 만들었다(멱등)").isFalse();
        assertThat(series.isDue(sunday.plusWeeks(1))).isTrue();

        series.end(SAT.plusDays(1));
        assertThat(series.isActive()).isFalse();
        assertThat(series.isDue(sunday.plusWeeks(1))).isFalse();
    }
}
