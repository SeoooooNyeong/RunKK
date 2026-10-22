package com.a207.runkk.runimal;

import com.a207.runkk.common.DateRange;
import com.a207.runkk.running.RunQueryService;
import com.a207.runkk.running.RunSampleRow;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 축 평가 표본을 묶음 조회 4번으로 만든다(6.5 "Runimal 표본": RunQueryService 2번, CourseVerdictRepository,
 * CrewRunParticipationQuery). RunimalService의 생성자 의존이 Fan-Out 7을 넘지 않게 표본 조립만 떼었다. 크루런 참가 조회
 * 구현(crew)이 아직 없으면 크루런 참가 기록이 없다고 본다.
 */
@Component
class RunSampleReader {

    /** 최근 유효 기록 수(FR-153). */
    static final int RECENT_RUNS = 10;

    private final RunQueryService runQueryService;
    private final CourseVerdictRepository courseVerdictRepository;
    private final ObjectProvider<CrewRunParticipationQuery> participationQuery;

    RunSampleReader(
            RunQueryService runQueryService,
            CourseVerdictRepository courseVerdictRepository,
            ObjectProvider<CrewRunParticipationQuery> participationQuery) {
        this.runQueryService = runQueryService;
        this.courseVerdictRepository = courseVerdictRepository;
        this.participationQuery = participationQuery;
    }

    /** 최근 10회 + 최근 한 달(동행 축) + 코스 판정 + 크루런 연결 기록. */
    RunSample read(long memberId, LocalDate today) {
        List<RunSampleRow> recent = runQueryService.runSamples(memberId, RECENT_RUNS);
        DateRange month = new DateRange(today.minusMonths(1), today);
        List<RunSampleRow> lastMonth = runQueryService.runSamples(memberId, month);
        Set<String> ids =
                Stream.concat(recent.stream(), lastMonth.stream())
                        .map(RunSampleRow::recordId)
                        .collect(Collectors.toSet());
        Map<String, Boolean> verdicts =
                ids.isEmpty() ? Map.of() : courseVerdictRepository.findByRecords(ids);
        Set<String> linked =
                participationQuery
                        .getIfAvailable(() -> (member, range) -> Set.of())
                        .linkedRecordIds(memberId, month);
        return RunSample.of(recent, lastMonth, verdicts, linked);
    }
}
