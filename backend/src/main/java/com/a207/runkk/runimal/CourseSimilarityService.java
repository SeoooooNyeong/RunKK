package com.a207.runkk.runimal;

import com.a207.runkk.common.GeoPoint;
import com.a207.runkk.policy.CourseSimilarityRule;
import com.a207.runkk.policy.OperationPolicy;
import com.a207.runkk.running.RunQueryService;
import com.a207.runkk.running.RunSampleRow;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 새 기록의 코스 동일성 판정(설계 6.1.8, FR-166, SQ-10). 같은 회원의 최근 유효 30회 중 경로가 남은 기록과 비교한다(경로 파기 기록은
 * routePoints가 빈 목록). 비교 대상이 없으면 저장하지 않는다.
 */
@Service
public class CourseSimilarityService {

    private final RunQueryService runQueryService;
    private final CourseVerdictRepository courseVerdictRepository;
    private final CourseSimilarityJudge courseSimilarityJudge;
    private final OperationPolicy operationPolicy;
    private final Clock clock;

    public CourseSimilarityService(
            RunQueryService runQueryService,
            CourseVerdictRepository courseVerdictRepository,
            CourseSimilarityJudge courseSimilarityJudge,
            OperationPolicy operationPolicy,
            Clock clock) {
        this.runQueryService = runQueryService;
        this.courseVerdictRepository = courseVerdictRepository;
        this.courseSimilarityJudge = courseSimilarityJudge;
        this.operationPolicy = operationPolicy;
        this.clock = clock;
    }

    /** RunimalService.onRunSaved가 사건의 memberId·recordId를 넘긴다(design-review.md M19). */
    @Transactional
    public void judge(long memberId, String recordId) {
        List<GeoPoint> track = runQueryService.routePoints(recordId);
        if (track.isEmpty()) {
            return;
        }
        CourseSimilarityRule rule = operationPolicy.courseSimilarityRule();
        List<List<GeoPoint>> previous =
                runQueryService.runSamples(memberId, rule.compareCount() + 1).stream()
                        .map(RunSampleRow::recordId)
                        .filter(id -> !id.equals(recordId))
                        .limit(rule.compareCount())
                        .map(runQueryService::routePoints)
                        .filter(points -> !points.isEmpty())
                        .toList();
        if (previous.isEmpty()) {
            return;
        }
        boolean same = courseSimilarityJudge.isSameCourse(track, previous, rule);
        courseVerdictRepository.save(
                new CourseVerdict(recordId, memberId, same, LocalDateTime.now(clock)));
    }
}
