package com.a207.runkk.web.crew;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.DisplayName;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.PageCursor;
import com.a207.runkk.common.PersonalityTag;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.common.ServerZone;
import com.a207.runkk.common.StandardDistance;
import com.a207.runkk.common.TagOwner;
import com.a207.runkk.common.TagSet;
import com.a207.runkk.crew.AlbumView;
import com.a207.runkk.crew.AttendanceAnswer;
import com.a207.runkk.crew.AttendanceCounts;
import com.a207.runkk.crew.AttendanceView;
import com.a207.runkk.crew.CrewCreateCommand;
import com.a207.runkk.crew.CrewDetailView;
import com.a207.runkk.crew.CrewInfoChange;
import com.a207.runkk.crew.CrewListView;
import com.a207.runkk.crew.CrewName;
import com.a207.runkk.crew.CrewProfileEdit;
import com.a207.runkk.crew.CrewRole;
import com.a207.runkk.crew.CrewRunDraft;
import com.a207.runkk.crew.CrewRunResultView;
import com.a207.runkk.crew.CrewSearch;
import com.a207.runkk.crew.CrewVisibility;
import com.a207.runkk.crew.FinalProgress;
import com.a207.runkk.crew.JoinPolicy;
import com.a207.runkk.crew.LobbyView;
import com.a207.runkk.crew.MeetupDraft;
import com.a207.runkk.crew.MyCrewView;
import com.a207.runkk.crew.NoticeDraft;
import com.a207.runkk.crew.PollDraft;
import com.a207.runkk.crew.PollMethod;
import com.a207.runkk.crew.PollResultView;
import com.a207.runkk.crew.PostKind;
import com.a207.runkk.crew.PostListView;
import com.a207.runkk.crew.PostQuery;
import com.a207.runkk.crew.PostSummary;
import com.a207.runkk.crew.RaceStatusView;
import com.a207.runkk.crew.Readiness;
import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 크루 REST 전송 형식(설계 5.7.1, 부록 F F.2.2). 거리는 미터, 시간은 초, 시각은 오프셋 포함 ISO-8601(OffsetDateTime,
 * D-84·D-114, 서버 시간대 Asia/Seoul), 날짜는 yyyy-MM-dd. 앱 api.crew DTO와 같은 항목 이름을 쓰고, 도메인 값으로 바꾸는 일만 한다.
 */
final class CrewDtos {

    /** 로고 이미지 주소 상한(crew.logo_url 열). */
    static final int MAX_LOGO_URL = 500;

    /** 서버 시간대(bootstrap Clock과 같다). 도메인 시각(LocalDateTime)을 이 시간대의 오프셋으로 내보낸다. */
    static final ZoneId SEOUL = ServerZone.ID;

    /** 게시 사진 주소(GET /crew-photos/{photoId}, D-100). */
    static final String PHOTO_PATH = "/api/v1/crew-photos/";

    private CrewDtos() {}

    static String displayOf(DisplayName name) {
        return name == null ? null : name.value();
    }

    /** 1·3·5·10km 미터 → 기준 거리. 아니면 CREW_RUN_INVALID. */
    static StandardDistance crewRunDistance(Long meters) {
        return Arrays.stream(StandardDistance.values())
                .filter(StandardDistance::isCrewRun)
                .filter(d -> meters != null && d.distance().meters() == meters)
                .findFirst()
                .orElseThrow(() -> new BusinessRuleException(ReasonCode.CREW_RUN_INVALID));
    }

    static Long meters(Distance d) {
        return d == null ? null : d.meters();
    }

    /** 도메인 시각 → 오프셋 포함 시각(D-114). */
    static OffsetDateTime at(LocalDateTime time) {
        return time == null ? null : time.atZone(SEOUL).toOffsetDateTime();
    }

    /** 요청 시각 → 서버 시간대의 도메인 시각. */
    static LocalDateTime local(OffsetDateTime time) {
        return time == null ? null : time.atZoneSameInstant(SEOUL).toLocalDateTime();
    }

    static String photoUrl(long photoId) {
        return PHOTO_PATH + photoId;
    }

    /**
     * 크루 생성·정보 변경. 변경(PATCH)이면 비운 항목은 바꾸지 않는다.
     *
     * @param name 크루명
     * @param intro 한줄소개
     * @param tags 성향 태그
     * @param visibility 공개 여부
     * @param joinPolicy 가입 방식
     * @param logoUrl 로고 이미지 주소
     */
    record CrewFormRequest(
            String name,
            String intro,
            Set<PersonalityTag> tags,
            CrewVisibility visibility,
            JoinPolicy joinPolicy,
            @Pattern(regexp = "^https://[^\\s]+$") @Size(max = MAX_LOGO_URL) String logoUrl) {

        CrewCreateCommand toCommand() {
            return new CrewCreateCommand(name, intro, tags, visibility, joinPolicy, logoUrl);
        }

        CrewInfoChange toChange() {
            return new CrewInfoChange(
                    name == null ? null : CrewName.of(name),
                    intro,
                    tags == null ? null : TagSet.of(tags, TagOwner.CREW),
                    visibility,
                    joinPolicy,
                    logoUrl);
        }
    }

    /**
     * 생성 결과.
     *
     * @param id 크루 ID
     * @param inviteCode 초대 코드
     * @param inviteCodeExpiresAt 만료 일시
     */
    record CreatedResponse(long id, String inviteCode, OffsetDateTime inviteCodeExpiresAt) {}

    /**
     * 초대 코드 재발급 결과.
     *
     * @param inviteCode 새 코드
     * @param inviteCodeExpiresAt 만료 일시
     */
    record InviteCodeResponse(String inviteCode, OffsetDateTime inviteCodeExpiresAt) {}

    /**
     * 크루장 양도.
     *
     * @param memberId 새 크루장
     */
    record LeaderRequest(@NotNull Long memberId) {}

    /**
     * 가입 신청: 크루 ID 또는 초대 코드.
     *
     * @param crewId 크루 ID
     * @param inviteCode 초대 코드
     */
    record JoinRequestBody(Long crewId, String inviteCode) {}

    /**
     * 가입 신청 결과.
     *
     * @param status JOINED | PENDING | CANCELLED
     * @param crewId 크루 ID
     * @param requestId 가입 신청 ID
     */
    record JoinResponse(String status, long crewId, Long requestId) {}

    /**
     * 가입 결정 결과.
     *
     * @param requestId 가입 신청 ID
     * @param status 신청 상태
     * @param reason 소멸 사유
     */
    record DecisionResponse(long requestId, String status, String reason) {}

    /**
     * 크루 내 표시 정보 변경.
     *
     * @param intro 크루 내 소개
     */
    record CrewProfileRequest(String intro) {

        CrewProfileEdit toEdit() {
            return new CrewProfileEdit(intro);
        }
    }

    /**
     * 크루 목록(v0.11: 추천·태그 일치 수 삭제, FR-118 삭제, D-91).
     *
     * @param crews 공개 크루
     * @param nextCursor 다음 페이지 커서(size를 줬을 때만), 없으면 null
     */
    record CrewListResponse(List<Item> crews, Long nextCursor) {

        static CrewListResponse of(CrewListView view) {
            return new CrewListResponse(
                    view.crews().stream().map(Item::of).toList(), view.nextCursor());
        }

        /**
         * 크루 한 줄(크루 밖 열람 범위만).
         *
         * @param crewId 크루 ID
         * @param name 크루명
         * @param intro 한줄소개
         * @param tags 성향 태그
         * @param logoUrl 로고
         * @param leaderName 크루장 표시명
         * @param headcount 인원
         * @param weeklyDistanceMeters 이번 주 크루 거리
         * @param joinPolicy 가입 방식
         */
        record Item(
                long crewId,
                String name,
                String intro,
                Set<PersonalityTag> tags,
                String logoUrl,
                String leaderName,
                int headcount,
                long weeklyDistanceMeters,
                JoinPolicy joinPolicy) {

            static Item of(CrewListView.Item i) {
                return new Item(
                        i.row().crewId(),
                        i.row().name(),
                        i.row().intro(),
                        i.row().tags().tags(),
                        i.row().logoUrl(),
                        displayOf(i.leaderName()),
                        i.row().headcount(),
                        i.row().weeklyDistance().meters(),
                        i.row().joinPolicy());
            }
        }
    }

    /**
     * 크루 주간 현황.
     *
     * @param weekStart 주 시작일
     * @param totalMeters 합산 거리
     * @param closesAt 마감 일시
     * @param contributors 기여자
     * @param myShare 본인 기여율
     * @param goalMeters 이번 주 공동 목표(v0.11 FR-138), 없으면 null
     * @param goalProgressPercent 목표 진행률 min(100, 합산 × 100 ÷ 목표), 목표가 없으면 null
     * @param achievedAt 목표 달성 일시, 아니면 null
     * @param celebration 축하 카드(달성한 주만, FR-217)
     */
    record RaceStatusResponse(
            LocalDate weekStart,
            long totalMeters,
            OffsetDateTime closesAt,
            List<Contributor> contributors,
            int myShare,
            Long goalMeters,
            Integer goalProgressPercent,
            OffsetDateTime achievedAt,
            Celebration celebration) {

        static RaceStatusResponse of(RaceStatusView v, long viewerId) {
            return new RaceStatusResponse(
                    v.weekStart(),
                    v.total().meters(),
                    at(v.closesAt()),
                    v.contributors().stream()
                            .map(
                                    c ->
                                            new Contributor(
                                                    displayOf(c.name()),
                                                    c.distance().meters(),
                                                    c.rate(),
                                                    c.memberId(),
                                                    c.memberId() != null
                                                            && c.memberId() == viewerId))
                            .toList(),
                    v.myRate(),
                    meters(v.goal()),
                    v.goalProgressPercent(),
                    at(v.achievedAt()),
                    v.celebration() == null
                            ? null
                            : new Celebration(
                                    v.celebration().crewName(),
                                    v.celebration().totalMeters().meters(),
                                    v.celebration().goalMeters().meters(),
                                    v.celebration().participantCount()));
        }

        /**
         * 공동 목표 축하 카드(FR-217).
         *
         * @param crewName 크루명
         * @param totalMeters 합산 거리
         * @param goalMeters 목표 거리
         * @param participantCount 참여 인원
         */
        record Celebration(
                String crewName, long totalMeters, long goalMeters, int participantCount) {}

        /**
         * 기여자 한 명.
         *
         * @param displayName 표시명
         * @param distanceMeters 기여 거리
         * @param share 기여율
         * @param memberId 기여자 회원 ID, 탈퇴했으면 null
         * @param me 열람자 본인
         */
        record Contributor(
                String displayName, long distanceMeters, int share, Long memberId, boolean me) {}
    }

    /**
     * 게시글 작성(부록 F F.2.2). 응원글(CHEER)은 body만, 크루런(CREW_RUN)은
     * body·distanceMeters·scheduledAt·voteDeadline, 대면 모임(MEETUP)은 scheduledAt·placeName·capacity,
     * 공지(NOTICE)는 body·pinned, 투표(POLL)는 body(질문)·pollMethod·options·closesAt. 게시글(BOARD)은
     * multipart POST /crews/{id}/board-posts로 쓴다.
     *
     * @param kind 게시 종류
     * @param body 본문(투표는 질문)
     * @param distanceMeters 크루런 거리
     * @param scheduledAt 크루런·대면 모임 일시
     * @param voteDeadline 크루런 참석 응답 마감, 비우면 일시
     * @param placeName 대면 모임 장소명(1~50자)
     * @param capacity 대면 모임 정원(2~10), 비우면 크루 정원
     * @param pinned 공지 고정, 기본 false
     * @param pollMethod 투표 방식
     * @param options 투표 선택지(CHOICES 2~5개, YES_NO는 비운다)
     * @param closesAt 투표 마감 일시
     */
    record PostWriteRequest(
            @NotNull PostKind kind,
            String body,
            Long distanceMeters,
            @JsonAlias("meetAt") OffsetDateTime scheduledAt,
            OffsetDateTime voteDeadline,
            String placeName,
            Integer capacity,
            Boolean pinned,
            PollMethod pollMethod,
            List<String> options,
            OffsetDateTime closesAt) {

        CrewRunDraft toCrewRunDraft() {
            return new CrewRunDraft(
                    body, local(scheduledAt), crewRunDistance(distanceMeters), local(voteDeadline));
        }

        /** 장소명·정원 범위 위반은 MeetupDraft 생성자의 IllegalArgumentException(400, D-155). */
        MeetupDraft toMeetupDraft() {
            return new MeetupDraft(body, local(scheduledAt), placeName, capacity);
        }

        NoticeDraft toNoticeDraft() {
            return new NoticeDraft(body, Boolean.TRUE.equals(pinned));
        }

        /** 질문·선택지 길이·개수 위반은 PollDraft 생성자의 IllegalArgumentException(400, D-155). */
        PollDraft toPollDraft() {
            return new PollDraft(body, pollMethod, options, local(closesAt));
        }
    }

    /**
     * 만든 게시글.
     *
     * @param id 게시글 ID
     */
    record PostResponse(long id) {}

    /**
     * 일정 참석 응답(v0.11 PUT /posts/{id}/attendance, FR-145).
     *
     * @param answer GOING | NOT_GOING | UNDECIDED
     */
    record AttendanceRequest(@NotNull AttendanceAnswer answer) {}

    /**
     * 참석 응답 결과.
     *
     * @param postId 게시글 ID
     * @param myAnswer 본인 응답
     * @param goingCount 참석 인원
     * @param notGoingCount 불참 인원
     * @param undecidedCount 미정 인원
     */
    record AttendanceResponse(
            long postId,
            AttendanceAnswer myAnswer,
            int goingCount,
            int notGoingCount,
            int undecidedCount) {

        static AttendanceResponse of(AttendanceView v) {
            return new AttendanceResponse(
                    v.postId(),
                    v.myAnswer(),
                    v.goingCount(),
                    v.notGoingCount(),
                    v.undecidedCount());
        }
    }

    /**
     * 공지 고정(v0.11 PUT /posts/{id}/pin, FR-205).
     *
     * @param pinned 고정 여부
     */
    record PinRequest(@NotNull Boolean pinned) {}

    /**
     * 공지 고정 결과.
     *
     * @param postId 게시글 ID
     * @param pinned 고정 여부
     */
    record PinResponse(long postId, boolean pinned) {}

    /**
     * 투표 응답(v0.11 PUT /posts/{id}/poll-response, FR-207). 마감 전에는 바꿀 수 있다.
     *
     * @param optionNo 고른 선택지 번호(1~5)
     */
    record PollAnswerRequest(@NotNull Integer optionNo) {}

    /**
     * 투표 결과(v0.11 GET /posts/{id}/poll, FR-207). 응답자는 익명이 아니다.
     *
     * @param postId 게시글 ID
     * @param question 질문
     * @param method 투표 방식
     * @param closesAt 마감 일시
     * @param closed 마감됨
     * @param options 선택지별 결과
     * @param myOptionNo 본인이 고른 선택지, 없으면 null
     */
    record PollResultResponse(
            long postId,
            String question,
            PollMethod method,
            OffsetDateTime closesAt,
            boolean closed,
            List<Option> options,
            Integer myOptionNo) {

        static PollResultResponse of(PollResultView v) {
            return new PollResultResponse(
                    v.postId(),
                    v.question(),
                    v.method(),
                    at(v.closesAt()),
                    v.closed(),
                    v.options().stream()
                            .map(
                                    o ->
                                            new Option(
                                                    o.optionNo(),
                                                    o.label(),
                                                    o.count(),
                                                    o.respondents().stream()
                                                            .map(
                                                                    r ->
                                                                            new Respondent(
                                                                                    r.memberId(),
                                                                                    displayOf(
                                                                                            r
                                                                                                    .displayName())))
                                                            .toList()))
                            .toList(),
                    v.myOptionNo());
        }

        /**
         * 선택지 하나.
         *
         * @param optionNo 번호
         * @param label 이름
         * @param count 응답 수
         * @param respondents 응답자
         */
        record Option(int optionNo, String label, int count, List<Respondent> respondents) {}

        /**
         * 응답자.
         *
         * @param memberId 회원 ID
         * @param displayName 표시명
         */
        record Respondent(long memberId, String displayName) {}
    }

    /**
     * 크루 앨범(v0.11 GET /crews/{id}/album, FR-212). 최신순.
     *
     * @param photos 사진
     * @param nextCursor 다음 페이지 커서(photoId), 없으면 null
     */
    record AlbumResponse(List<Photo> photos, Long nextCursor) {

        static AlbumResponse of(AlbumView v) {
            return new AlbumResponse(
                    v.photos().stream()
                            .map(
                                    p ->
                                            new Photo(
                                                    p.photoId(),
                                                    p.postId(),
                                                    photoUrl(p.photoId()),
                                                    at(p.postedAt())))
                            .toList(),
                    v.nextCursor());
        }

        /**
         * 앨범 사진.
         *
         * @param photoId 사진 ID
         * @param postId 게시글 ID
         * @param url 사진 주소(/api/v1/crew-photos/{photoId})
         * @param postedAt 게시 일시
         */
        record Photo(long photoId, long postId, String url, OffsetDateTime postedAt) {}
    }

    /**
     * 이번 주 공동 목표(v0.11 PUT /crews/{id}/goal, FR-138).
     *
     * @param goalMeters 목표 거리(m, 1,000 이상)
     */
    record GoalRequest(@NotNull Long goalMeters) {}

    /**
     * 운영진 지정·해제 결과(v0.11 PUT·DELETE /crews/{id}/managers/{memberId}, FR-204).
     *
     * @param memberId 대상 회원 ID
     * @param role 지정이면 MANAGER, 해제면 MEMBER
     */
    record ManagerResponse(long memberId, CrewRole role) {}

    /**
     * 게시글 목록.
     *
     * @param posts 글, 최신순
     * @param nextCursor 다음 페이지 커서(size를 줬을 때만), 없으면 null
     */
    record PostListResponse(List<Item> posts, Long nextCursor) {

        /** now는 투표 마감 여부(closed)를 정하는 조회 시각이다. */
        static PostListResponse of(PostListView view, LocalDateTime now) {
            return new PostListResponse(
                    view.posts().stream().map(line -> Item.of(line, now)).toList(),
                    view.nextCursor());
        }

        /**
         * 글 한 줄.
         *
         * @param postId 게시글 ID
         * @param kind 게시 종류
         * @param authorName 작성자 표시명
         * @param body 본문
         * @param writtenAt 작성 일시
         * @param mine 내가 쓴 글
         * @param crewRun 크루런 정보(모집글만)
         * @param canDelete 지울 수 있음(본인 글 또는 운영진 이상, v0.11 FR-213)
         * @param meetup 대면 모임 정보(MEETUP만)
         * @param notice 공지 정보(NOTICE만)
         * @param poll 투표 요약(POLL만)
         * @param photos 사진(BOARD만)
         */
        record Item(
                long postId,
                String kind,
                String authorName,
                String body,
                OffsetDateTime writtenAt,
                boolean mine,
                CrewRun crewRun,
                boolean canDelete,
                Meetup meetup,
                Notice notice,
                Poll poll,
                List<Photo> photos) {

            static Item of(PostListView.Line line, LocalDateTime now) {
                PostSummary summary = line.summary();
                return new Item(
                        summary.postId(),
                        summary.kind().name(),
                        displayOf(line.authorName()),
                        summary.body(),
                        at(summary.writtenAt()),
                        summary.mine(),
                        summary.crewRun() == null
                                ? null
                                : CrewRun.of(summary.crewRun(), line.crewContribution()),
                        line.canDelete(),
                        summary.meetup() == null ? null : Meetup.of(summary.meetup()),
                        summary.notice() == null ? null : new Notice(summary.notice().pinned()),
                        summary.poll() == null ? null : Poll.of(summary.poll(), now),
                        summary.photoIds() == null
                                ? null
                                : summary.photoIds().stream()
                                        .map(id -> new Photo(id, photoUrl(id)))
                                        .toList());
            }
        }

        /**
         * 대면 모임 정보(v0.11 FR-132).
         *
         * @param scheduledAt 일시
         * @param placeName 장소명
         * @param capacity 정원
         * @param goingCount 참석 인원
         * @param notGoingCount 불참 인원
         * @param undecidedCount 미정 인원
         * @param myAnswer 본인 응답, 없으면 null
         */
        record Meetup(
                OffsetDateTime scheduledAt,
                String placeName,
                int capacity,
                int goingCount,
                int notGoingCount,
                int undecidedCount,
                AttendanceAnswer myAnswer) {

            static Meetup of(PostSummary.MeetupInfo m) {
                AttendanceCounts c = m.counts();
                return new Meetup(
                        at(m.scheduledAt()),
                        m.placeName(),
                        m.capacity(),
                        c.going(),
                        c.notGoing(),
                        c.undecided(),
                        m.myAnswer());
            }
        }

        /**
         * 공지 정보(v0.11 FR-205).
         *
         * @param pinned 고정됨
         */
        record Notice(boolean pinned) {}

        /**
         * 투표 요약(v0.11 FR-206).
         *
         * @param method 투표 방식
         * @param closesAt 마감 일시
         * @param closed 마감됨
         * @param responseCount 응답 수
         * @param myOptionNo 본인이 고른 선택지, 없으면 null
         */
        record Poll(
                PollMethod method,
                OffsetDateTime closesAt,
                boolean closed,
                int responseCount,
                Integer myOptionNo) {

            static Poll of(PostSummary.PollInfo p, LocalDateTime now) {
                return new Poll(
                        p.method(),
                        at(p.closesAt()),
                        p.closedAt(now),
                        p.responseCount(),
                        p.myOptionNo());
            }
        }

        /**
         * 게시글 사진(v0.11 FR-211).
         *
         * @param photoId 사진 ID
         * @param url 사진 주소(/api/v1/crew-photos/{photoId})
         */
        record Photo(long photoId, String url) {}

        /**
         * 모집글의 크루런 정보.
         *
         * @param distanceMeters 크루런 거리
         * @param scheduledAt 모집희망일시
         * @param voteDeadline 투표 마감
         * @param status 크루런 상태
         * @param goingCount 참석 인원
         * @param myAnswer 본인 응답(GOING | NOT_GOING | UNDECIDED)
         * @param finisherCount 완주 인원
         * @param raceContributionMeters 크루 반영 거리(지난 크루런)
         * @param notGoingCount 불참 인원(v0.11 FR-133)
         * @param undecidedCount 미정 인원(v0.11 FR-133)
         */
        record CrewRun(
                long distanceMeters,
                OffsetDateTime scheduledAt,
                OffsetDateTime voteDeadline,
                String status,
                int goingCount,
                String myAnswer,
                int finisherCount,
                Long raceContributionMeters,
                int notGoingCount,
                int undecidedCount) {

            static CrewRun of(PostSummary.CrewRunInfo run, Distance contribution) {
                return new CrewRun(
                        run.distance().distance().meters(),
                        at(run.scheduledAt()),
                        at(run.voteDeadline()),
                        run.status().name(),
                        run.counts().going(),
                        run.myAnswer() == null ? null : run.myAnswer().name(),
                        run.finisherCount(),
                        meters(contribution),
                        run.counts().notGoing(),
                        run.counts().undecided());
            }
        }
    }

    /**
     * 크루런 대기실.
     *
     * @param postId 게시글 ID
     * @param scheduledAt 모집희망일시
     * @param distanceMeters 크루런 거리
     * @param remainingSeconds 남은 시간
     * @param status 크루런 상태
     * @param participants 참가자 준비 상태
     */
    record LobbyResponse(
            long postId,
            OffsetDateTime scheduledAt,
            long distanceMeters,
            long remainingSeconds,
            String status,
            List<Participant> participants) {

        static LobbyResponse of(LobbyView v, long viewerId) {
            return new LobbyResponse(
                    v.postId(),
                    at(v.scheduledAt()),
                    v.distance().distance().meters(),
                    v.remaining().toSeconds(),
                    v.status().name(),
                    v.participants().stream()
                            .map(
                                    p ->
                                            new Participant(
                                                    p.memberId(),
                                                    displayOf(p.name()),
                                                    p.readiness().name(),
                                                    p.readiness() == Readiness.READY,
                                                    p.memberId() == viewerId))
                            .toList());
        }

        /**
         * 참가자 한 명.
         *
         * @param memberId 회원 ID
         * @param displayName 표시명
         * @param readiness NOT_ENTERED | ENTERING | READY
         * @param ready 준비 완료
         * @param me 열람자 본인
         */
        record Participant(
                long memberId, String displayName, String readiness, boolean ready, boolean me) {}
    }

    /**
     * 크루런 결과(합산 방식, S15P31A207-236).
     *
     * @param postId 게시글 ID
     * @param status 크루런 상태
     * @param title 모집글 본문 첫 줄, 없으면 null
     * @param distanceMeters 크루런 거리(목표)
     * @param startedAt 출발 일시, 출발 전이면 null
     * @param totalDistanceMeters 참가자 거리 합
     * @param participants 진행 참가자(대기실 입장 순)
     * @param myRecordId 열람자의 연결된 기록 ID, 없으면 null
     * @param firstCrewRun 첫 크루런
     * @param raceContributionMeters 크루 반영 거리 합
     */
    record CrewRunResultResponse(
            long postId,
            String status,
            String title,
            long distanceMeters,
            OffsetDateTime startedAt,
            long totalDistanceMeters,
            List<Participant> participants,
            String myRecordId,
            boolean firstCrewRun,
            long raceContributionMeters) {

        static CrewRunResultResponse of(CrewRunResultView v, long viewerId) {
            return new CrewRunResultResponse(
                    v.postId(),
                    v.status().name(),
                    v.title(),
                    v.distance().distance().meters(),
                    v.startedAt() == null ? null : at(v.startedAt()),
                    v.totalDistance().meters(),
                    v.participants().stream().map(l -> Participant.of(l, viewerId)).toList(),
                    v.myRecordId(),
                    v.firstCrewRun(),
                    v.crewContribution().meters());
        }

        /**
         * 참가자 결과.
         *
         * @param memberId 참가자 회원 ID, 탈퇴했으면 null
         * @param displayName 표시명
         * @param distanceMeters 거리
         * @param elapsedSeconds 시간(초), 없으면 null
         * @param averagePaceSecondsPerKm 평균 페이스(초/km), 없으면 null
         * @param ended 끝냈는가
         * @param me 열람자 본인
         */
        record Participant(
                Long memberId,
                String displayName,
                long distanceMeters,
                Long elapsedSeconds,
                Integer averagePaceSecondsPerKm,
                boolean ended,
                boolean me) {

            static Participant of(CrewRunResultView.Line l, long viewerId) {
                return new Participant(
                        l.memberId(),
                        displayOf(l.name()),
                        l.distance().meters(),
                        l.elapsed() == null ? null : l.elapsed().toSeconds(),
                        l.averagePace() == null ? null : l.averagePace().secondsPerKm(),
                        l.ended(),
                        l.memberId() != null && l.memberId() == viewerId);
            }
        }
    }

    /**
     * 일시정지 본문(합산 방식). 본문이 없거나 값이 비면 서버가 최신 진행값의 경과를 쓴다.
     *
     * @param elapsedSeconds 정지 때 경과(초)
     */
    record PauseRequest(Long elapsedSeconds) {

        Duration elapsed() {
            if (elapsedSeconds == null) {
                return null;
            }
            if (elapsedSeconds < 0) {
                throw new BusinessRuleException(ReasonCode.CREW_RUN_INVALID);
            }
            return Duration.ofSeconds(elapsedSeconds);
        }
    }

    /**
     * 내 크루런 끝내기 본문(합산 방식). 본문이 없으면 서버가 세션의 최신 진행값을 쓴다.
     *
     * @param distanceMeters 마지막 누적 거리
     * @param elapsedSeconds 마지막 경과(초)
     */
    record QuitRequest(Long distanceMeters, Long elapsedSeconds) {

        FinalProgress toFinal() {
            if (distanceMeters == null && elapsedSeconds == null) {
                return null;
            }
            if (distanceMeters == null
                    || elapsedSeconds == null
                    || distanceMeters < 0
                    || elapsedSeconds < 0) {
                throw new BusinessRuleException(ReasonCode.CREW_RUN_INVALID);
            }
            return new FinalProgress(
                    Distance.ofMeters(distanceMeters), Duration.ofSeconds(elapsedSeconds));
        }
    }

    /**
     * 크루 한 곳(GET /crews/{id}, GET /crews/me의 crew).
     *
     * @param crewId 크루 ID
     * @param name 크루명
     * @param intro 한줄소개
     * @param tags 성향 태그
     * @param visibility PUBLIC | PRIVATE
     * @param joinPolicy FREE | APPROVAL
     * @param logoUrl 로고
     * @param leaderId 크루장 회원 ID
     * @param leaderName 크루장 표시명
     * @param headcount 인원
     * @param weeklyDistanceMeters 이번 주 크루 거리
     * @param myRole LEADER | MANAGER | MEMBER, 크루 밖이면 null
     * @param inviteCode 초대 코드(크루장만)
     * @param inviteCodeExpiresAt 초대 코드 만료 일시(크루장만)
     */
    record CrewDetailResponse(
            long crewId,
            String name,
            String intro,
            Set<PersonalityTag> tags,
            CrewVisibility visibility,
            JoinPolicy joinPolicy,
            String logoUrl,
            long leaderId,
            String leaderName,
            int headcount,
            long weeklyDistanceMeters,
            String myRole,
            String inviteCode,
            OffsetDateTime inviteCodeExpiresAt) {

        static CrewDetailResponse of(CrewDetailView v) {
            return new CrewDetailResponse(
                    v.crewId(),
                    v.name(),
                    v.intro(),
                    v.tags().tags(),
                    v.visibility(),
                    v.joinPolicy(),
                    v.logoUrl(),
                    v.leaderId(),
                    displayOf(v.leaderName()),
                    v.headcount(),
                    v.weeklyDistance().meters(),
                    v.myRole() == null ? null : v.myRole().name(),
                    v.inviteCode() == null ? null : v.inviteCode().code(),
                    v.inviteCode() == null ? null : at(v.inviteCode().expiresAt()));
        }
    }

    /**
     * 본인 크루 현황(GET /crews/me).
     *
     * @param crew 소속 크루, 없으면 null
     * @param pendingRequests 대기 중인 본인 가입 신청
     */
    record MyCrewResponse(CrewDetailResponse crew, List<PendingJoin> pendingRequests) {

        static MyCrewResponse of(MyCrewView v) {
            return new MyCrewResponse(
                    v.crew() == null ? null : CrewDetailResponse.of(v.crew()),
                    v.pendingRequests().stream()
                            .map(
                                    p ->
                                            new PendingJoin(
                                                    p.requestId(),
                                                    p.crewId(),
                                                    p.crewName(),
                                                    at(p.requestedAt())))
                            .toList());
        }

        /**
         * 대기 중인 가입 신청 한 건(취소는 DELETE /join-requests/{requestId}).
         *
         * @param requestId 가입 신청 ID
         * @param crewId 크루 ID
         * @param crewName 크루명
         * @param requestedAt 신청 일시
         */
        record PendingJoin(
                long requestId, long crewId, String crewName, OffsetDateTime requestedAt) {}
    }

    /**
     * 크루 검색 쿼리(GET /crews, 코드 리뷰 2차 P2). 쿼리 이름은 항목 이름과 같다.
     *
     * @param keyword 이름 검색어
     * @param tags 성향 태그(여러 개는 tags=A&tags=B 또는 tags=A,B)
     * @param cursor 다음 페이지 커서
     * @param size 페이지 크기(없으면 전부)
     */
    record CrewSearchQuery(String keyword, Set<PersonalityTag> tags, Long cursor, Integer size) {

        CrewSearch toSearch() {
            return new CrewSearch(tags, keyword);
        }

        PageCursor page() {
            return new PageCursor(cursor, size);
        }
    }

    /**
     * 크루 게시글 목록 쿼리(GET /crews/{id}/posts, F.2.2). 쿼리 이름은 항목 이름과 같다.
     *
     * @param kinds 글 종류(쉼표 또는 반복), 없으면 전부
     * @param kind v0.10 단수 종류(호환), kinds와 함께 오면 합친다
     * @param pinnedOnly 고정 공지만
     * @param cursor 다음 페이지 커서
     * @param size 페이지 크기(없으면 전부)
     */
    record PostListQuery(
            Set<PostKind> kinds, PostKind kind, Boolean pinnedOnly, Long cursor, Integer size) {

        PostQuery toQuery() {
            Set<PostKind> all = EnumSet.noneOf(PostKind.class);
            if (kinds != null) {
                all.addAll(kinds);
            }
            if (kind != null) {
                all.add(kind);
            }
            return new PostQuery(
                    all, Boolean.TRUE.equals(pinnedOnly), new PageCursor(cursor, size));
        }
    }

    /**
     * 크루 앨범 쿼리(GET /crews/{id}/album, FR-212).
     *
     * @param cursor 다음 페이지 커서(photoId)
     * @param size 페이지 크기(없으면 30)
     */
    record AlbumQuery(Long cursor, Integer size) {

        PageCursor page() {
            return new PageCursor(cursor, size);
        }
    }
}
