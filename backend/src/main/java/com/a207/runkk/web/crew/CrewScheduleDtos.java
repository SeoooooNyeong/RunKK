package com.a207.runkk.web.crew;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.crew.AttendanceAnswer;
import com.a207.runkk.crew.CrewRunDetailView;
import com.a207.runkk.crew.CrewRunEdit;
import com.a207.runkk.crew.CrewRunRepeat;
import com.a207.runkk.crew.CrewRunStatus;
import com.a207.runkk.crew.MyRunState;
import com.a207.runkk.crew.PastCrewRunsView;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 크루 일정 W6 전송 형식(피그마 F-11b·F-11d·F-13·F-13d, S15P31A207-297). 계약 원본은
 * docs/sw/maintenance/2026-11-09_피그마맞춤-성/contract-W6.md이고 공통 형식(미터, 오프셋 포함 시각)은 CrewDtos와 같다.
 */
final class CrewScheduleDtos {

    /** 월 쿼리 형식(2000~2099년, 런바디 월과 같다). */
    private static final Pattern YEAR_MONTH = Pattern.compile("20[0-9]{2}-(0[1-9]|1[0-2])");

    private CrewScheduleDtos() {}

    /** yyyy-MM → 월. 형식이 아니면 INVALID_YEAR_MONTH(400). */
    static YearMonth month(String value) {
        if (value == null || !YEAR_MONTH.matcher(value).matches()) {
            throw new BusinessRuleException(ReasonCode.INVALID_YEAR_MONTH);
        }
        return YearMonth.parse(value);
    }

    /** yyyy-MM-dd → 날짜, 비우면 null. 형식이 아니면 INVALID_YEAR_MONTH(400). */
    static LocalDate day(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new BusinessRuleException(ReasonCode.INVALID_YEAR_MONTH);
        }
    }

    /**
     * 모집글 수정(PATCH /crew-runs/{id}). 화면의 네 칸을 모두 보낸다(빠진 칸을 "그대로"로 보지 않는다). 일시는 바꿀 수 없다.
     *
     * @param title 제목(1~30자)
     * @param body 내용(0~200자), null이면 빈 문자열
     * @param distanceMeters 거리, null이면 제한 없음. 3000·5000·10000·21097, 또는 지금 거리와 같은 값
     * @param repeat NONE이면 반복을 끝내고 WEEKLY면 이 글로 반복을 시작한다
     */
    record CrewRunEditRequest(
            @NotNull String title,
            String body,
            Long distanceMeters,
            @NotNull CrewRunRepeat repeat) {

        CrewRunEdit toEdit() {
            return new CrewRunEdit(title, body, CrewDtos.crewRunDistance(distanceMeters), repeat);
        }
    }

    /**
     * 크루런 일정 상세(GET·PATCH /crew-runs/{id}).
     *
     * @param postId 게시글 ID
     * @param crewId 크루 ID
     * @param title 제목
     * @param body 내용, 없으면 빈 문자열
     * @param authorId 작성자, 탈퇴했으면 null
     * @param authorName 작성자 표시명(탈퇴면 "탈퇴한 사용자")
     * @param mine 내가 쓴 글
     * @param writtenAt 작성 일시
     * @param scheduledAt 일시
     * @param voteDeadline 참석 응답 마감
     * @param distanceMeters 거리, 제한 없음이면 null
     * @param status VOTING | LOBBY_OPEN | RUNNING | FINISHED | CANCELLED
     * @param goingCount 참석 인원(attendees 길이)
     * @param myAnswer 내 응답(GOING | NOT_GOING | UNDECIDED), 없거나 참석 취소했으면 null
     * @param canEdit ⋯ 메뉴 수정하기(작성자이고 VOTING이고 일시 전)
     * @param canDelete ⋯ 메뉴 삭제하기(작성자 또는 크루장·운영진)
     * @param repeat 진행 중인 반복의 다가오는 회차면 WEEKLY("매주" 배지), 아니면 NONE
     * @param repeatDayOfWeek 반복 요일(MONDAY~SUNDAY), NONE이면 null
     * @param attendees 참석자(작성자가 참석 중이면 맨 앞, 나머지는 응답 순)
     * @param myRun 열람자의 참여 상태 NONE | RUNNING | ENDED(v0.14 S15P31A207-314)
     */
    record CrewRunDetailResponse(
            long postId,
            long crewId,
            String title,
            String body,
            Long authorId,
            String authorName,
            boolean mine,
            OffsetDateTime writtenAt,
            OffsetDateTime scheduledAt,
            OffsetDateTime voteDeadline,
            Long distanceMeters,
            CrewRunStatus status,
            int goingCount,
            AttendanceAnswer myAnswer,
            boolean canEdit,
            boolean canDelete,
            CrewRunRepeat repeat,
            String repeatDayOfWeek,
            List<Attendee> attendees,
            MyRunState myRun) {

        /** faceKeys: 참석자 얼굴 키(AvatarQuery.faceKeysOf). 얼굴을 고르지 않았거나 탈퇴한 참석자는 없다. */
        static CrewRunDetailResponse of(CrewRunDetailView v, Map<Long, String> faceKeys) {
            return new CrewRunDetailResponse(
                    v.postId(),
                    v.crewId(),
                    v.title(),
                    v.body(),
                    v.authorId(),
                    CrewDtos.displayOf(v.authorName()),
                    v.mine(),
                    CrewDtos.at(v.writtenAt()),
                    CrewDtos.at(v.scheduledAt()),
                    CrewDtos.at(v.voteDeadline()),
                    CrewDtos.meters(v.distance()),
                    v.status(),
                    v.goingCount(),
                    v.myAnswer(),
                    v.canEdit(),
                    v.canDelete(),
                    v.repeat(),
                    v.repeatDayOfWeek() == null ? null : v.repeatDayOfWeek().name(),
                    v.attendees().stream()
                            .map(
                                    a ->
                                            new Attendee(
                                                    a.memberId(),
                                                    CrewDtos.displayOf(a.name()),
                                                    a.author(),
                                                    a.me(),
                                                    a.memberId() == null
                                                            ? null
                                                            : faceKeys.get(a.memberId()),
                                                    a.shownGender(),
                                                    a.distance() == null
                                                            ? null
                                                            : a.distance().meters()))
                            .toList(),
                    v.myRun());
        }

        /**
         * 참석자 한 명.
         *
         * @param memberId 회원 ID, 탈퇴했으면 null
         * @param displayName 표시명(탈퇴면 "탈퇴한 사용자")
         * @param author 작성자("작성자" 꼬리표)
         * @param me 나
         * @param faceKey 아바타 얼굴 키(대기실과 같은 값), 없으면 null
         * @param shownGender 표시 성별(MALE·FEMALE), 없으면 null
         * @param distanceMeters 마친(FINISHED) 크루런에서 달린 거리(결과 화면과 같은 규칙: 연결 기록 → 끝낸 값), 마치지 않았거나 달리지
         *     않았으면 null
         */
        record Attendee(
                Long memberId,
                String displayName,
                boolean author,
                boolean me,
                String faceKey,
                String shownGender,
                Long distanceMeters) {}
    }

    /**
     * 지난 크루런 연·월(GET /crews/{id}/crew-runs/past, F-11b·F-11c, W7 달력).
     *
     * @param month 조회한 월(yyyy-MM)
     * @param dates 그 달에 지난 크루런이 있는 날짜(yyyy-MM-dd, 오름차순)
     * @param runs 그 달(date를 주면 그날)의 지난 크루런, 일시 최신순
     * @param earliestMonth 가장 이른 지난 크루런 월(yyyy-MM), 없으면 null
     */
    record PastCrewRunsResponse(
            String month, List<String> dates, List<PastRun> runs, String earliestMonth) {

        static PastCrewRunsResponse of(PastCrewRunsView v) {
            return new PastCrewRunsResponse(
                    v.month().toString(),
                    v.dates().stream().map(LocalDate::toString).toList(),
                    v.runs().stream().map(PastRun::of).toList(),
                    v.earliestMonth() == null ? null : v.earliestMonth().toString());
        }

        /**
         * 지난 크루런 한 줄.
         *
         * @param postId 게시글 ID(상세 GET /crew-runs/{postId}, 참석자 거리 GET /crew-runs/{postId}/result)
         * @param title 제목
         * @param scheduledAt 일시
         * @param distanceMeters 거리, 제한 없음이면 null
         * @param status FINISHED | CANCELLED
         * @param goingCount 참석 인원
         * @param runnerCount 함께 달린 인원(취소면 0)
         */
        record PastRun(
                long postId,
                String title,
                OffsetDateTime scheduledAt,
                Long distanceMeters,
                CrewRunStatus status,
                int goingCount,
                int runnerCount) {

            static PastRun of(PastCrewRunsView.Run r) {
                return new PastRun(
                        r.postId(),
                        r.title(),
                        CrewDtos.at(r.scheduledAt()),
                        CrewDtos.meters(r.distance()),
                        r.status(),
                        r.goingCount(),
                        r.runnerCount());
            }
        }
    }
}
