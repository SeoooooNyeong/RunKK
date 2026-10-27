package com.a207.runkk.crew;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.ReasonCode;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 크루 투표 하나의 선택지와 응답(설계 6.1.7, FR-206, FR-207). 질문은 본문(1~200자)이다. 마감은 now ≥ closesAt(정각 포함, D-165).
 * reminded는 마감 1시간 전 알림을 보냈는가(크루런 10분 전 알림과 같은 칼럼, D-106).
 */
@Entity
@DiscriminatorValue("POLL")
public class PollPost extends CrewPost {

    /** 찬반 투표 선택지(FR-206). */
    static final String YES = "찬성";

    static final String NO = "반대";

    @Enumerated(EnumType.STRING)
    @Column(name = "poll_method", length = 10)
    private PollMethod method;

    @Column(name = "poll_closes_at")
    private LocalDateTime closesAt;

    @Column(name = "reminded")
    private boolean reminded;

    @ElementCollection
    @CollectionTable(name = "crew_poll_option", joinColumns = @JoinColumn(name = "post_id"))
    @OrderBy("optionNo")
    private List<PollOption> options = new ArrayList<>();

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PollResponse> responses = new ArrayList<>();

    protected PollPost() {}

    /**
     * 마감이 지금 이전(같음 포함)이면 POLL_INVALID(409, D-155). YES_NO면 선택지 "찬성"(1)·"반대"(2)를 만들고, CHOICES면 입력
     * 순서로 1번부터 번호를 붙인다. 질문·선택지 길이·개수는 PollDraft가 먼저 막는다.
     */
    public PollPost(long crewId, long authorId, PollDraft draft, LocalDateTime now) {
        super(crewId, authorId, draft.question(), now);
        if (!draft.closesAt().isAfter(now)) {
            throw new BusinessRuleException(ReasonCode.POLL_INVALID);
        }
        this.method = draft.method();
        this.closesAt = draft.closesAt();
        List<String> labels = method == PollMethod.YES_NO ? List.of(YES, NO) : draft.options();
        for (int i = 0; i < labels.size(); i++) {
            options.add(new PollOption(i + 1, labels.get(i)));
        }
    }

    @Override
    protected int maxBody() {
        return SHORT_BODY;
    }

    @Override
    public PostKind kind() {
        return PostKind.POLL;
    }

    /** 응답·변경(FR-207). 마감 뒤면 POLL_CLOSED, 없는 선택지면 POLL_INVALID. 회원당 한 응답이고 마감 전에는 바꿀 수 있다. */
    public void respond(long memberId, int optionNo, LocalDateTime now) {
        if (isClosed(now)) {
            throw new BusinessRuleException(ReasonCode.POLL_CLOSED);
        }
        if (options.stream().noneMatch(o -> o.optionNo() == optionNo)) {
            throw new BusinessRuleException(ReasonCode.POLL_INVALID);
        }
        responseOf(memberId)
                .ifPresentOrElse(
                        r -> r.change(optionNo, now),
                        () -> responses.add(new PollResponse(this, memberId, optionNo, now)));
    }

    /** closesAt이 지났는가(now ≥ closesAt, 정각 포함). */
    public boolean isClosed(LocalDateTime now) {
        return !now.isBefore(closesAt);
    }

    /** 주어진 현재 크루원 중 응답이 없는 회원(마감 임박 알림 대상, FR-218). */
    public Set<Long> nonRespondents(Collection<Long> memberIds) {
        Set<Long> answered =
                responses.stream().map(PollResponse::memberId).collect(Collectors.toSet());
        return memberIds.stream()
                .filter(id -> !answered.contains(id))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** 마감 임박 알림 보냄. */
    public void markReminded() {
        reminded = true;
    }

    public boolean isReminded() {
        return reminded;
    }

    /** 질문 + 방식·마감·응답 수·본인 선택. */
    @Override
    public PostSummary summary(long viewerId) {
        return baseSummary(viewerId)
                .withPoll(
                        new PostSummary.PollInfo(
                                method,
                                closesAt,
                                responses.size(),
                                responseOf(viewerId).map(PollResponse::optionNo).orElse(null)));
    }

    public Optional<PollResponse> responseOf(long memberId) {
        return responses.stream().filter(r -> r.memberId() == memberId).findFirst();
    }

    public List<PollOption> options() {
        return Collections.unmodifiableList(options);
    }

    public List<PollResponse> responses() {
        return Collections.unmodifiableList(responses);
    }

    public PollMethod method() {
        return method;
    }

    public LocalDateTime closesAt() {
        return closesAt;
    }
}
