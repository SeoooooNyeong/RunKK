package com.a207.runkk.crew;

import com.a207.runkk.notification.NavigationTarget;
import com.a207.runkk.notification.NotificationRequest;
import com.a207.runkk.notification.NotificationService;
import com.a207.runkk.notification.NotificationType;
import com.a207.runkk.notification.Screen;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 크루런 매주 반복(피그마 F-12f·F-13e, 사용자 결정 C8, W6). 규칙은 contract-W6.md 3절이다.
 *
 * <ul>
 *   <li>매주 일요일 오전 9시(batch.crew.CrewRunRepeatJob, D-성-26, 코드 리뷰 M6) generateDue가 다음 주(월~일)에 들어가는 같은
 *       요일·시각 회차 하나를 최신 회차에서 복사해 만든다. 서버가 멈춰 놓친 주는 건너뛴다.
 *   <li>반복 행을 잠가 다시 조건을 보고 만들며, crew_post (series_id, scheduled_at) 고유 색인이 두 번째 삽입을 막는다(멱등).
 *   <li>잠금 순서는 언제나 반복 → 글이다(회차 생성·글 수정·글 삭제·크루 삭제). 글을 먼저 잠근 뒤 반복을 기다리는 경로가 없어야 교착이 생기지 않는다(코드 리뷰
 *       M1).
 *   <li>수정(NONE)·최신 회차 삭제·작성자 이탈(탈퇴·퇴장)·최신 회차 숨김이면 반복을 끝낸다. 크루를 지우면 반복 행도 지운다.
 * </ul>
 */
@Component
public class CrewRunRepeater {

    private static final Logger log = LoggerFactory.getLogger(CrewRunRepeater.class);

    private final CrewRunSeriesRepository seriesRepository;
    private final CrewPostRepository crewPostRepository;
    private final CrewRepository crewRepository;
    private final NotificationService notificationService;
    private final CrewTransactions transactions;

    CrewRunRepeater(
            CrewRunSeriesRepository seriesRepository,
            CrewPostRepository crewPostRepository,
            CrewRepository crewRepository,
            NotificationService notificationService,
            CrewTransactions transactions) {
        this.seriesRepository = seriesRepository;
        this.crewPostRepository = crewPostRepository;
        this.crewRepository = crewRepository;
        this.notificationService = notificationService;
        this.transactions = transactions;
    }

    /** 저장된 글(첫 회차)로 반복을 시작하고 글을 반복에 묶는다. 부르는 쪽 트랜잭션 안에서 쓴다. */
    void start(CrewRunPost post, LocalDateTime now) {
        CrewRunSeries series = seriesRepository.save(CrewRunSeries.start(post, post.getId(), now));
        post.attachSeries(series.getId());
        crewPostRepository.save(post);
    }

    /**
     * 글 행을 잠그기 전에 그 글이 속한 반복 행을 먼저 잠근다(반복 → 글 순서, 코드 리뷰 M1). 글을 잠그지 않고 읽은 seriesId를 쓴다. 크루런이 아니거나
     * 반복이 없으면 아무것도 하지 않는다.
     */
    void lockSeriesBeforePost(CrewPost unlocked) {
        if (unlocked instanceof CrewRunPost run) {
            lockedSeriesOf(run);
        }
    }

    /**
     * 수정의 반복 칸(F-13e). WEEKLY면 이 글이 진행 중인 반복에 속하지 않을 때 이 글로 새 반복을 시작하고, NONE이면 이 글이 속한 진행 중 반복을
     * 끝낸다. lockSeriesBeforePost로 반복을 먼저 잠그고 글 행을 잠근 트랜잭션 안에서 부른다.
     */
    void apply(CrewRunPost post, CrewRunRepeat repeat, LocalDateTime now) {
        Optional<CrewRunSeries> current = lockedSeriesOf(post).filter(CrewRunSeries::isActive);
        if (repeat == CrewRunRepeat.WEEKLY) {
            if (current.isEmpty()) {
                start(post, now);
            }
            return;
        }
        current.ifPresent(series -> end(series, now));
    }

    /**
     * 글 삭제: 이 글이 진행 중인 반복의 최신 회차면 반복을 끝낸다. 지난 회차 삭제는 반복에 영향이 없다. lockSeriesBeforePost로 반복을 먼저 잠그고 글
     * 행을 잠근 트랜잭션 안에서 부른다.
     */
    void stopIfLatest(CrewRunPost post, LocalDateTime now) {
        lockedSeriesOf(post)
                .filter(series -> series.isLatest(post.getId()))
                .ifPresent(series -> end(series, now));
    }

    /** 이 글이 진행 중인 반복의 다가오는 회차(일시 &gt; now)면 그 반복("매주" 배지). 지난 회차는 비어 있다. 읽기 전용. */
    Optional<CrewRunSeries> repeatingSeriesOf(CrewRunPost post, LocalDateTime now) {
        if (post.seriesId() == null || !post.scheduledAt().isAfter(now)) {
            return Optional.empty();
        }
        return seriesRepository.findById(post.seriesId()).filter(CrewRunSeries::isActive);
    }

    private Optional<CrewRunSeries> lockedSeriesOf(CrewRunPost post) {
        if (post.seriesId() == null || post.getId() == null) {
            return Optional.empty();
        }
        return seriesRepository.findByIdForUpdate(post.seriesId());
    }

    private void end(CrewRunSeries series, LocalDateTime now) {
        series.end(now);
        seriesRepository.save(series);
    }

    /**
     * 차례인 반복마다 새 트랜잭션에서 다음 회차를 하나 만든다. 한 반복의 실패(동시 생성의 고유 색인 충돌 등)는 그 반복만 건너뛰고 다음 차례에 다시 본다. 만든 회차
     * 수를 돌려준다.
     */
    public int generateDue(LocalDateTime now) {
        LocalDateTime before = CrewRunSeries.windowEnd(now).minusDays(CrewRunSeries.INTERVAL_DAYS);
        List<Long> due = transactions.read(() -> seriesRepository.findActiveIdsBefore(before));
        int created = 0;
        for (long seriesId : due) {
            if (transactions.once("crew_run_repeat", () -> generate(seriesId, now), false)) {
                created++;
            }
        }
        return created;
    }

    /**
     * 반복 행 → 최신 회차 글 행 순서로 잠근다. 최신 회차를 잠가 다시 읽어야 막 커밋된 삭제·숨김을 보고 반복을 끝낸다(지운 글을 복사하지 않는다, 코드 리뷰 M1).
     */
    private boolean generate(long seriesId, LocalDateTime now) {
        Optional<CrewRunSeries> found = seriesRepository.findByIdForUpdate(seriesId);
        if (found.isEmpty() || !found.get().isDue(now)) {
            return false; // 다른 실행이 먼저 만들었거나 끝냈다, 크루를 지웠다
        }
        CrewRunSeries series = found.get();
        Optional<Crew> crew = crewRepository.findById(series.crewId());
        Optional<CrewRunPost> latest =
                crewPostRepository
                        .findByIdForUpdate(series.lastPostId())
                        .filter(CrewPost::isVisible)
                        .filter(CrewRunPost.class::isInstance)
                        .map(CrewRunPost.class::cast);
        if (crew.isEmpty()
                || !crew.get().activeMemberIds().contains(series.authorId())
                || latest.isEmpty()
                || latest.get().authorId() == null) {
            log.info("crew_run_repeat_ended series={} reason=author_or_post_gone", seriesId);
            end(series, now);
            return false;
        }
        LocalDateTime slot = series.nextSlot(now);
        CrewRunPost next = latest.get().nextOccurrence(seriesId, slot, now);
        crewPostRepository.save(next);
        series.advance(next.getId(), slot);
        seriesRepository.save(series);
        announce(crew.get(), next);
        return true;
    }

    /** 직접 쓴 모집글과 같은 CREW_RUN_RECRUIT를 작성자를 뺀 크루원에게(FR-193). */
    private void announce(Crew crew, CrewRunPost post) {
        NavigationTarget target =
                new NavigationTarget(Screen.CREW_RUN_RECRUIT, String.valueOf(post.getId()));
        crew.activeMemberIds().stream()
                .filter(id -> !id.equals(post.authorId()))
                .forEach(
                        id ->
                                notificationService.notify(
                                        new NotificationRequest(
                                                id,
                                                NotificationType.CREW_RUN_RECRUIT,
                                                CrewPostService.RECRUIT_TEXT,
                                                target)));
    }
}
