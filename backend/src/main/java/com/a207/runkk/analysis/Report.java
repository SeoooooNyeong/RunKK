package com.a207.runkk.analysis;

import com.a207.runkk.running.RunningScore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.DayOfWeek;
import java.time.LocalDateTime;

/**
 * 회원의 최근 10회 리포트(설계 6.1.4 Report, FR-064~FR-067). 회원마다 한 행이다. 요약이 바뀌면 코멘트를 비우고, 늦게 온 다른 요약의 코멘트는
 * 버린다. 같은 사건을 다시 받아도 코멘트가 지워지지 않는다(T-16).
 */
@Entity
@Table(name = "report")
public class Report {

    @Id
    @Column(name = "member_id")
    private Long memberId;

    @Column(name = "from_run", nullable = false)
    private int fromRun;

    @Column(name = "to_run", nullable = false)
    private int toRun;

    @Column(name = "average_score", nullable = false)
    private double averageScore;

    @Column(name = "distance_change_meters", nullable = false)
    private long distanceChangeMeters;

    @Column(name = "pace_change_seconds", nullable = false)
    private int paceChangeSeconds;

    @Enumerated(EnumType.STRING)
    @Column(name = "frequent_day", nullable = false, length = 10)
    private DayOfWeek frequentDay;

    @Column(name = "frequent_hour_band", nullable = false)
    private int frequentHourBand;

    @Column(name = "frequency_change", nullable = false)
    private double frequencyChange;

    @Column(name = "comment_text", length = 500)
    private String commentText;

    @Enumerated(EnumType.STRING)
    @Column(name = "comment_source", length = 10)
    private CommentSource commentSource;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** null이면 아직 저장 전(새 행). */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected Report() {}

    private Report(long memberId) {
        this.memberId = memberId;
    }

    /** 첫 요약을 담은 리포트. */
    public static Report create(long memberId, FitnessSummary summary, LocalDateTime now) {
        Report report = new Report(memberId);
        report.replace(summary, now);
        return report;
    }

    public long getMemberId() {
        return memberId;
    }

    /**
     * 요약 교체. s.toRun이 지금 요약과 같거나 더 앞이면 무시한다(같은 사건 재수신에 코멘트 유지, 늦게 처리된 앞 사건이 새 요약을 덮지 않음). 바뀌면 코멘트를
     * 비운다(코멘트 준비 중). 요약을 바꿨으면 true.
     */
    public boolean refresh(FitnessSummary s, LocalDateTime now) {
        if (s.toRun() <= toRun) {
            return false;
        }
        replace(s, now);
        return true;
    }

    /** 요약 순번이 다른(늦게 온) 코멘트는 버린다. 붙였으면 true. */
    public boolean attachComment(Comment c) {
        if (c.forRun() != toRun) {
            return false;
        }
        this.commentText = c.text();
        this.commentSource = c.source();
        return true;
    }

    public boolean hasComment() {
        return commentText != null;
    }

    public FitnessSummary summary() {
        return new FitnessSummary(
                fromRun,
                toRun,
                new RunningScore(averageScore),
                distanceChangeMeters,
                paceChangeSeconds,
                frequentDay,
                frequentHourBand,
                frequencyChange);
    }

    /** 조회 결과. 코멘트가 없으면 "코멘트 준비 중"(FR-067). */
    public ReportView view() {
        return ReportView.of(
                summary(),
                hasComment() ? new Comment(commentText, commentSource, toRun) : null,
                createdAt);
    }

    private void replace(FitnessSummary s, LocalDateTime now) {
        this.fromRun = s.fromRun();
        this.toRun = s.toRun();
        this.averageScore = s.averageScore().value();
        this.distanceChangeMeters = s.distanceChangeMeters();
        this.paceChangeSeconds = s.paceChangeSeconds();
        this.frequentDay = s.frequentDay();
        this.frequentHourBand = s.frequentHourBand();
        this.frequencyChange = s.frequencyChange();
        this.commentText = null;
        this.commentSource = null;
        this.createdAt = now;
    }
}
