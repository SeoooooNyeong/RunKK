package com.a207.runkk.web.crew;

import com.a207.runkk.crew.GoalHistoryView;
import com.a207.runkk.crew.WeeklyRaceService;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 지난 공동 목표 목록(피그마 맞춤 W7 F-21, contract-W7.md 4장). 크루원만. 앞 페이지의 nextBefore를 before로 넘겨 다음 페이지를 읽는다.
 * 같은 시기에 다른 작업이 CrewController를 함께 고쳐 엔드포인트 하나를 따로 둔다.
 */
@RestController
@RequestMapping("/api/v1")
public class CrewGoalHistoryController {

    private static final String MEMBER_ID = "memberId";
    private static final String DEFAULT_SIZE = "20";

    private final WeeklyRaceService weeklyRaceService;

    CrewGoalHistoryController(WeeklyRaceService weeklyRaceService) {
        this.weeklyRaceService = weeklyRaceService;
    }

    @GetMapping("/crews/{id}/goals/history")
    GoalHistoryResponse goalHistory(
            @RequestAttribute(MEMBER_ID) long memberId,
            @PathVariable("id") long crewId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate before,
            @RequestParam(defaultValue = DEFAULT_SIZE) int size) {
        return GoalHistoryResponse.of(
                weeklyRaceService.goalHistory(memberId, crewId, Optional.ofNullable(before), size));
    }

    /**
     * 지난 공동 목표 한 페이지.
     *
     * @param goals 목표가 있던 지난 주, 최신 주부터
     * @param nextBefore 다음 페이지 before, 마지막 페이지면 null
     */
    record GoalHistoryResponse(List<Goal> goals, LocalDate nextBefore) {

        static GoalHistoryResponse of(GoalHistoryView view) {
            return new GoalHistoryResponse(
                    view.goals().stream()
                            .map(
                                    g ->
                                            new Goal(
                                                    g.weekStart(),
                                                    g.month(),
                                                    g.weekOfMonth(),
                                                    g.goal().meters(),
                                                    g.total().meters(),
                                                    g.achieved(),
                                                    g.progressPercent(),
                                                    g.participantCount()))
                            .toList(),
                    view.nextBefore());
        }

        /**
         * 한 주.
         *
         * @param weekStart 그 주 월요일
         * @param month "N월"(그 주 목요일의 달)
         * @param weekOfMonth "N주차"
         * @param goalMeters 목표 거리(m)
         * @param totalMeters 합계 거리(m)
         * @param achieved 달성 여부
         * @param progressPercent 진행률, 100 초과 허용
         * @param participantCount 참여 인원
         */
        record Goal(
                LocalDate weekStart,
                int month,
                int weekOfMonth,
                long goalMeters,
                long totalMeters,
                boolean achieved,
                int progressPercent,
                int participantCount) {}
    }
}
