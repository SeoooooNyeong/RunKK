package com.a207.runkk.crew;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;

/**
 * 크루런 매주 반복 한 줄(피그마 F-12f, 사용자 결정 C8, W6). 회차의 제목·내용·거리·시각은 최신 회차(lastPostId)를 그대로 복사하므로 규칙을 따로
 * 저장하지 않는다. 매주 일요일 오전 9시 작업(D-성-26)이 다음 주(월~일)에 들어가는 회차 하나를 만든다: 최신 회차 + 7일(nextSlot)이 다음 주
 * 끝(windowEnd) 전이면 차례(isDue)다. 표 crew_run_series(V56). 시각은 서버 시간대(Asia/Seoul) LocalDateTime이고 한국은
 * 일광 절약 시간이 없어 +7일이 늘 같은 시각이다.
 */
@Entity
@Table(name = "crew_run_series")
public class CrewRunSeries {

    /** 반복 간격(일). */
    static final int INTERVAL_DAYS = 7;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "crew_id", nullable = false)
    private long crewId;

    @Column(name = "author_id", nullable = false)
    private long authorId;

    @Column(name = "last_post_id", nullable = false)
    private long lastPostId;

    @Column(name = "last_scheduled_at", nullable = false)
    private LocalDateTime lastScheduledAt;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected CrewRunSeries() {}

    private CrewRunSeries(
            long crewId, long authorId, long postId, LocalDateTime scheduledAt, LocalDateTime now) {
        this.crewId = crewId;
        this.authorId = authorId;
        this.lastPostId = postId;
        this.lastScheduledAt = scheduledAt;
        this.active = true;
        this.createdAt = now;
    }

    /** 첫 회차(작성자가 있는 저장된 글, ID는 postId)로 반복을 시작한다. 작성자가 없으면 IllegalStateException. */
    static CrewRunSeries start(CrewRunPost first, long postId, LocalDateTime now) {
        if (first.authorId() == null) {
            throw new IllegalStateException("작성자가 없는 글로 반복을 시작할 수 없다");
        }
        return new CrewRunSeries(
                first.crewId(), first.authorId(), postId, first.scheduledAt(), now);
    }

    /** 진행 중이고 다음 회차(nextSlot)가 다음 주 끝(windowEnd) 전이면 만들 차례다. 이미 만들었으면 다음 회차가 그 뒤라 거짓(멱등). */
    public boolean isDue(LocalDateTime now) {
        return active && nextSlot(now).isBefore(windowEnd(now));
    }

    /** now가 든 주(월~일)의 다음 주가 끝나는 시각(다음다음 주 월요일 0시). 일요일 9시면 내일부터 7일이 다음 주다. */
    public static LocalDateTime windowEnd(LocalDateTime now) {
        return now.toLocalDate()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .plusWeeks(2)
                .atStartOfDay();
    }

    /** 최신 회차 + 7일. 그래도 now 이전이면(서버가 여러 주 멈춤) 지난 주를 건너뛰고 now 뒤의 첫 같은 요일·시각. */
    LocalDateTime nextSlot(LocalDateTime now) {
        LocalDateTime next = lastScheduledAt.plusDays(INTERVAL_DAYS);
        while (!next.isAfter(now)) {
            next = next.plusDays(INTERVAL_DAYS);
        }
        return next;
    }

    /** 새 회차를 최신 회차로 삼는다. */
    void advance(long postId, LocalDateTime scheduledAt) {
        this.lastPostId = postId;
        this.lastScheduledAt = scheduledAt;
    }

    /** 반복을 끝낸다(이미 끝났으면 그대로). */
    void end(LocalDateTime now) {
        if (active) {
            active = false;
            endedAt = now;
        }
    }

    /** 이 글이 진행 중인 반복의 최신 회차인가(지우면 반복이 끝난다). */
    boolean isLatest(long postId) {
        return active && lastPostId == postId;
    }

    public DayOfWeek dayOfWeek() {
        return lastScheduledAt.getDayOfWeek();
    }

    public boolean isActive() {
        return active;
    }

    public Long getId() {
        return id;
    }

    public long crewId() {
        return crewId;
    }

    public long authorId() {
        return authorId;
    }

    public long lastPostId() {
        return lastPostId;
    }
}
