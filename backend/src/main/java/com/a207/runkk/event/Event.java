package com.a207.runkk.event;

import com.a207.runkk.common.Timestamps;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * 앱 이벤트 하나(S15P31A207-275, V35_2). 운영자가 관리 API로 만들고 고치며, 목록은 앱이 GET /events로 읽고 상세는 detailUrl의 웹
 * 페이지(nginx 정적 파일)다. 기간은 한국 시간이고 양 끝을 포함한다.
 */
@Entity
@Table(name = "event")
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "slug", nullable = false, unique = true, length = 40)
    private String slug;

    @Column(name = "title", nullable = false, length = 60)
    private String title;

    @Column(name = "subtitle", nullable = false, length = 100)
    private String subtitle;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 20)
    private EventKind kind;

    @Column(name = "config", nullable = false, columnDefinition = "text")
    private String config;

    @Column(name = "starts_at", nullable = false)
    private LocalDateTime startsAt;

    @Column(name = "ends_at", nullable = false)
    private LocalDateTime endsAt;

    @Column(name = "banner_image_url", nullable = false, length = 300)
    private String bannerImageUrl;

    @Column(name = "thumbnail_image_url", nullable = false, length = 300)
    private String thumbnailImageUrl;

    @Column(name = "detail_url", nullable = false, length = 300)
    private String detailUrl;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "published", nullable = false)
    private boolean published;

    @Embedded private Timestamps timestamps;

    protected Event() {}

    /** 새 이벤트. 입력 검사는 EventService가 먼저 한다. */
    static Event create(EventInput in, LocalDateTime now) {
        Event event = new Event();
        event.timestamps = Timestamps.createdAt(now);
        event.apply(in);
        return event;
    }

    /** 모든 항목을 입력 값으로 바꾼다. */
    void update(EventInput in, LocalDateTime now) {
        apply(in);
        timestamps = timestamps.touched(now);
    }

    /** 공개 여부만 바꾼다. */
    void changePublished(boolean value, LocalDateTime now) {
        published = value;
        timestamps = timestamps.touched(now);
    }

    private void apply(EventInput in) {
        slug = in.slug();
        title = in.title();
        subtitle = in.subtitle();
        kind = in.kind();
        config = in.config();
        startsAt = in.startsAt();
        endsAt = in.endsAt();
        bannerImageUrl = in.bannerImageUrl();
        thumbnailImageUrl = in.thumbnailImageUrl();
        detailUrl = in.detailUrl();
        sortOrder = in.sortOrder();
        published = in.published();
    }

    EventStatus statusAt(LocalDateTime now) {
        return EventStatus.of(startsAt, endsAt, now);
    }

    /** 공개 중이고 at이 기간 안(양 끝 포함)인가. */
    boolean isOpenAt(LocalDateTime at) {
        return published && statusAt(at) == EventStatus.ONGOING;
    }

    EventConfig parsedConfig() {
        return EventConfig.parse(kind, config);
    }

    public Long getId() {
        return id;
    }

    public String getSlug() {
        return slug;
    }

    public String getTitle() {
        return title;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public EventKind getKind() {
        return kind;
    }

    public String getConfig() {
        return config;
    }

    public LocalDateTime getStartsAt() {
        return startsAt;
    }

    public LocalDateTime getEndsAt() {
        return endsAt;
    }

    public String getBannerImageUrl() {
        return bannerImageUrl;
    }

    public String getThumbnailImageUrl() {
        return thumbnailImageUrl;
    }

    public String getDetailUrl() {
        return detailUrl;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public boolean isPublished() {
        return published;
    }
}
