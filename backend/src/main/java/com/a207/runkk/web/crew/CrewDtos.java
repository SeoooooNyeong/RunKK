package com.a207.runkk.web.crew;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.DisplayName;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.PersonalityTag;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.common.StandardDistance;
import com.a207.runkk.common.TagOwner;
import com.a207.runkk.common.TagSet;
import com.a207.runkk.crew.CrewCreateCommand;
import com.a207.runkk.crew.CrewInfoChange;
import com.a207.runkk.crew.CrewListView;
import com.a207.runkk.crew.CrewName;
import com.a207.runkk.crew.CrewProfileEdit;
import com.a207.runkk.crew.CrewRunDraft;
import com.a207.runkk.crew.CrewRunResultView;
import com.a207.runkk.crew.CrewVisibility;
import com.a207.runkk.crew.JoinPolicy;
import com.a207.runkk.crew.LobbyView;
import com.a207.runkk.crew.PostListView;
import com.a207.runkk.crew.PostSummary;
import com.a207.runkk.crew.RaceStatusView;
import com.a207.runkk.crew.Readiness;
import com.a207.runkk.crew.Recommendation;
import com.a207.runkk.crew.RecommendationView;
import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * 크루 REST 전송 형식(설계 5.7.1, 10/21 API 계약). 거리는 미터, 시간은 초, 시각은 서버 시간대 ISO-8601, 날짜는 yyyy-MM-dd. 앱
 * api.crew DTO와 같은 항목 이름을 쓰고, 도메인 값으로 바꾸는 일만 한다.
 */
final class CrewDtos {

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
            String logoUrl) {

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
    record CreatedResponse(long id, String inviteCode, LocalDateTime inviteCodeExpiresAt) {}

    /**
     * 초대 코드 재발급 결과.
     *
     * @param inviteCode 새 코드
     * @param inviteCodeExpiresAt 만료 일시
     */
    record InviteCodeResponse(String inviteCode, LocalDateTime inviteCodeExpiresAt) {}

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
     * @param tags 성향 태그, 비우면 그대로
     */
    record CrewProfileRequest(String intro, Set<PersonalityTag> tags) {

        CrewProfileEdit toEdit() {
            return new CrewProfileEdit(
                    intro, tags == null ? null : TagSet.of(tags, TagOwner.RUNNER));
        }
    }

    /**
     * 크루 목록.
     *
     * @param crews 공개 크루
     * @param recommendations 크루 없는 러너의 추천
     */
    record CrewListResponse(List<Item> crews, RecommendationsResponse recommendations) {

        static CrewListResponse of(CrewListView view) {
            return new CrewListResponse(
                    view.crews().stream().map(Item::of).toList(),
                    view.recommendations() == null
                            ? null
                            : RecommendationsResponse.of(view.recommendations()));
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
         * @param tagMatches 태그 일치 수
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
                JoinPolicy joinPolicy,
                int tagMatches) {

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
                        i.row().joinPolicy(),
                        i.tagMatches());
            }
        }
    }

    /**
     * 추천 크루 목록.
     *
     * @param crews 추천
     * @param guide 태그가 없을 때 안내문
     */
    record RecommendationsResponse(List<Item> crews, String guide) {

        static RecommendationsResponse of(RecommendationView view) {
            return new RecommendationsResponse(
                    view.crews().stream().map(Item::of).toList(), view.guide());
        }

        /**
         * 추천 한 건("태그 n개 일치 · 바로 가입/승인제").
         *
         * @param crewId 크루 ID
         * @param name 크루명
         * @param matchCount 일치도
         * @param tags 성향 태그
         * @param headcount 인원
         * @param joinPolicy 가입 방식
         */
        record Item(
                long crewId,
                String name,
                int matchCount,
                Set<PersonalityTag> tags,
                int headcount,
                JoinPolicy joinPolicy) {

            static Item of(Recommendation r) {
                return new Item(
                        r.crew().crewId(),
                        r.crew().name(),
                        r.matchCount(),
                        r.crew().tags().tags(),
                        r.crew().headcount(),
                        r.crew().joinPolicy());
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
     */
    record RaceStatusResponse(
            LocalDate weekStart,
            long totalMeters,
            LocalDateTime closesAt,
            List<Contributor> contributors,
            int myShare) {

        static RaceStatusResponse of(RaceStatusView v) {
            return new RaceStatusResponse(
                    v.weekStart(),
                    v.total().meters(),
                    v.closesAt(),
                    v.contributors().stream()
                            .map(
                                    c ->
                                            new Contributor(
                                                    displayOf(c.name()),
                                                    c.distance().meters(),
                                                    c.rate()))
                            .toList(),
                    v.myRate());
        }

        /**
         * 기여자 한 명.
         *
         * @param displayName 표시명
         * @param distanceMeters 기여 거리
         * @param share 기여율
         */
        record Contributor(String displayName, long distanceMeters, int share) {}
    }

    /**
     * 게시글 작성. 응원글(CHEER)은 body만, 크루런 모집글(CREW_RUN)은 body·distanceMeters·scheduledAt·voteDeadline.
     *
     * @param kind 게시 종류
     * @param body 본문
     * @param distanceMeters 크루런 거리
     * @param scheduledAt 모집희망일시
     * @param voteDeadline 투표 마감, 비우면 모집희망일시
     */
    record PostWriteRequest(
            @NotNull com.a207.runkk.crew.PostKind kind,
            String body,
            Long distanceMeters,
            @JsonAlias("meetAt") LocalDateTime scheduledAt,
            LocalDateTime voteDeadline) {

        CrewRunDraft toDraft() {
            return new CrewRunDraft(
                    body, scheduledAt, crewRunDistance(distanceMeters), voteDeadline);
        }
    }

    /**
     * 만든 게시글.
     *
     * @param id 게시글 ID
     */
    record PostResponse(long id) {}

    /**
     * 참여 투표.
     *
     * @param answer GOING | NOT_GOING
     */
    record VoteRequest(@NotNull com.a207.runkk.crew.VoteAnswer answer) {}

    /**
     * 게시글 목록.
     *
     * @param posts 글, 최신순
     */
    record PostListResponse(List<Item> posts) {

        static PostListResponse of(PostListView view) {
            return new PostListResponse(view.posts().stream().map(Item::of).toList());
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
         */
        record Item(
                long postId,
                String kind,
                String authorName,
                String body,
                LocalDateTime writtenAt,
                boolean mine,
                CrewRun crewRun) {

            static Item of(PostListView.Line line) {
                PostSummary s = line.summary();
                return new Item(
                        s.postId(),
                        s.kind().name(),
                        displayOf(line.authorName()),
                        s.body(),
                        s.writtenAt(),
                        s.mine(),
                        s.crewRun() == null
                                ? null
                                : CrewRun.of(s.crewRun(), line.crewContribution()));
            }
        }

        /**
         * 모집글의 크루런 정보.
         *
         * @param distanceMeters 크루런 거리
         * @param scheduledAt 모집희망일시
         * @param voteDeadline 투표 마감
         * @param status 크루런 상태
         * @param goingCount 참여 인원
         * @param myAnswer 본인 응답
         * @param finisherCount 완주 인원
         * @param raceContributionMeters 크루 반영 거리(지난 크루런)
         */
        record CrewRun(
                long distanceMeters,
                LocalDateTime scheduledAt,
                LocalDateTime voteDeadline,
                String status,
                int goingCount,
                String myAnswer,
                int finisherCount,
                Long raceContributionMeters) {

            static CrewRun of(PostSummary.CrewRunInfo run, Distance contribution) {
                return new CrewRun(
                        run.distance().distance().meters(),
                        run.scheduledAt(),
                        run.voteDeadline(),
                        run.status().name(),
                        run.goingCount(),
                        run.myAnswer() == null ? null : run.myAnswer().name(),
                        run.finisherCount(),
                        meters(contribution));
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
            LocalDateTime scheduledAt,
            long distanceMeters,
            long remainingSeconds,
            String status,
            List<Participant> participants) {

        static LobbyResponse of(LobbyView v) {
            return new LobbyResponse(
                    v.postId(),
                    v.scheduledAt(),
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
                                                    p.readiness() == Readiness.READY))
                            .toList());
        }

        /**
         * 참가자 한 명.
         *
         * @param memberId 회원 ID
         * @param displayName 표시명
         * @param readiness NOT_ENTERED | ENTERING | READY
         * @param ready 준비 완료
         */
        record Participant(long memberId, String displayName, String readiness, boolean ready) {}
    }

    /**
     * 크루런 결과.
     *
     * @param postId 게시글 ID
     * @param status 크루런 상태
     * @param finisherCount 완주 인원
     * @param finishers 진행 참가자(순위순)
     * @param firstCrewRun 첫 크루런
     * @param raceContributionMeters 크루 반영 거리 합
     */
    record CrewRunResultResponse(
            long postId,
            String status,
            int finisherCount,
            List<Finisher> finishers,
            boolean firstCrewRun,
            long raceContributionMeters) {

        static CrewRunResultResponse of(CrewRunResultView v) {
            return new CrewRunResultResponse(
                    v.postId(),
                    v.status().name(),
                    v.finisherCount(),
                    v.runners().stream().map(Finisher::of).toList(),
                    v.firstCrewRun(),
                    v.crewContribution().meters());
        }

        /**
         * 참가자 결과.
         *
         * @param rank 순위, 미완주면 null
         * @param displayName 표시명
         * @param elapsedSeconds 기록(초)
         * @param averagePaceSecondsPerKm 평균 페이스(초/km)
         * @param quit 그만둠
         */
        record Finisher(
                Integer rank,
                String displayName,
                Long elapsedSeconds,
                Integer averagePaceSecondsPerKm,
                boolean quit) {

            static Finisher of(CrewRunResultView.Line l) {
                return new Finisher(
                        l.rank(),
                        displayOf(l.name()),
                        l.elapsed() == null ? null : l.elapsed().toSeconds(),
                        l.averagePace() == null ? null : l.averagePace().secondsPerKm(),
                        l.quit());
            }
        }
    }
}
