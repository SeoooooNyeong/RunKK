package com.a207.runkk.web.record;

import com.a207.runkk.analysis.CoachService;
import com.a207.runkk.analysis.DailyPlan;
import com.a207.runkk.analysis.GoalResult;
import com.a207.runkk.analysis.TodayPlanView;
import com.a207.runkk.analysis.WeeklyGoalCommand;
import com.a207.runkk.common.DateRange;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.Pace;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 주간 목표·날짜별 계획·이행 현황·오늘 계획(설계 5.7.1 PUT /goals/weekly, PUT /plans/{date}, DELETE /plans/{date}, GET
 * /plans/progress, GET /plans/today → CoachService, FR-042, FR-072~FR-074, FR-187). 오늘 계획이 없으면
 * 204다. 계획 취소는 204이고 없는 계획을 지워도 204다. 범위 위반은 409(GOAL_INVALID·PLAN_INVALID·PAST_DATE).
 */
@RestController
@RequestMapping("/api/v1")
public class CoachController {

    private static final String MEMBER_ID = "memberId";

    private final CoachService coachService;

    public CoachController(CoachService coachService) {
        this.coachService = coachService;
    }

    @PutMapping("/goals/weekly")
    GoalResult setWeeklyGoal(
            @RequestAttribute(MEMBER_ID) long memberId,
            @Valid @RequestBody WeeklyGoalRequest request) {
        return coachService.setWeeklyGoal(
                memberId,
                new WeeklyGoalCommand(request.runs(), Distance.ofMeters(request.distanceMeters())));
    }

    @PutMapping("/plans/{date}")
    GoalResult putPlan(
            @RequestAttribute(MEMBER_ID) long memberId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Valid @RequestBody PlanRequest request) {
        Pace pace =
                request.paceSecondsPerKm() == null ? null : new Pace(request.paceSecondsPerKm());
        return coachService.putPlan(
                memberId, new DailyPlan(date, Distance.ofMeters(request.distanceMeters()), pace));
    }

    /** 그날 계획 취소(S15P31A207-275, 코치에서 같은 km를 다시 누름). 없으면 그대로 204, 지난 날짜는 409 PAST_DATE. */
    @DeleteMapping("/plans/{date}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deletePlan(
            @RequestAttribute(MEMBER_ID) long memberId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        coachService.deletePlan(memberId, date);
    }

    @GetMapping("/plans/progress")
    RecordDtos.PlanProgressResponse progress(
            @RequestAttribute(MEMBER_ID) long memberId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return RecordDtos.PlanProgressResponse.of(
                coachService.progress(memberId, new DateRange(from, to)));
    }

    @GetMapping("/plans/today")
    ResponseEntity<RecordDtos.TodayPlanResponse> todayPlan(
            @RequestAttribute(MEMBER_ID) long memberId) {
        TodayPlanView plan = coachService.todayPlan(memberId);
        return plan.hasPlan()
                ? ResponseEntity.ok(RecordDtos.TodayPlanResponse.of(plan))
                : ResponseEntity.noContent().build();
    }

    /**
     * 주간 목표(앱 WeeklyGoalRequest). 둘 다 있어야 한다(FR-072).
     *
     * @param runs 목표 횟수(1~7)
     * @param distanceMeters 목표 거리(미터)
     */
    record WeeklyGoalRequest(@NotNull Integer runs, @NotNull @Positive Long distanceMeters) {}

    /**
     * 날짜별 계획(앱 PlanRequest).
     *
     * @param distanceMeters 계획 거리(미터, 0 초과)
     * @param paceSecondsPerKm 목표 페이스(km당 초), 없으면 null
     */
    record PlanRequest(@Positive long distanceMeters, @Positive Integer paceSecondsPerKm) {}
}
