package com.a207.runkk.web.crew;

import com.a207.runkk.avatar.AvatarQuery;
import com.a207.runkk.crew.CrewRunDetailView;
import com.a207.runkk.crew.CrewScheduleService;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.Objects;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 크루 일정 엔드포인트(피그마 F-11b·F-11d·F-13·F-13b·F-13d, W6 S15P31A207-297): 일정 상세, 모집글 수정, 참석 취소, 지난 크루런
 * 연·월. 계약은 docs/sw/maintenance/2026-11-09_피그마맞춤-성/contract-W6.md. 작성·참석·삭제는 CrewPostController
 * 그대로다.
 */
@RestController
@RequestMapping("/api/v1")
public class CrewScheduleController {

    private static final String MEMBER_ID = "memberId";

    private final CrewScheduleService crewScheduleService;
    private final AvatarQuery avatarQuery;

    CrewScheduleController(CrewScheduleService crewScheduleService, AvatarQuery avatarQuery) {
        this.crewScheduleService = crewScheduleService;
        this.avatarQuery = avatarQuery;
    }

    /** 일정 상세(F-13, F-11d). 크루원만. */
    @GetMapping("/crew-runs/{id}")
    CrewScheduleDtos.CrewRunDetailResponse detail(
            @RequestAttribute(MEMBER_ID) long memberId, @PathVariable("id") long postId) {
        return response(crewScheduleService.detail(memberId, postId));
    }

    /** 모집글 수정(F-13d·F-13e). 작성자만, VOTING이고 일시 전. 바뀐 상세를 돌려준다. */
    @PatchMapping("/crew-runs/{id}")
    CrewScheduleDtos.CrewRunDetailResponse edit(
            @RequestAttribute(MEMBER_ID) long memberId,
            @PathVariable("id") long postId,
            @Valid @RequestBody CrewScheduleDtos.CrewRunEditRequest body) {
        return response(crewScheduleService.edit(memberId, postId, body.toEdit()));
    }

    /** 참석 취소(F-13b): 크루런 참석 응답을 거둔다. 참석은 PUT /posts/{id}/attendance(GOING)다. */
    @DeleteMapping("/posts/{id}/attendance")
    CrewDtos.AttendanceResponse withdrawAttendance(
            @RequestAttribute(MEMBER_ID) long memberId, @PathVariable("id") long postId) {
        return CrewDtos.AttendanceResponse.of(
                crewScheduleService.withdrawAttendance(memberId, postId));
    }

    /** 지난 크루런 연·월(F-11b·F-11c, W7 달력). month=yyyy-MM 필수, date=yyyy-MM-dd이면 runs만 그날. */
    @GetMapping("/crews/{id}/crew-runs/past")
    CrewScheduleDtos.PastCrewRunsResponse past(
            @RequestAttribute(MEMBER_ID) long memberId,
            @PathVariable("id") long crewId,
            @RequestParam("month") String month,
            @RequestParam(value = "date", required = false) String date) {
        return CrewScheduleDtos.PastCrewRunsResponse.of(
                crewScheduleService.past(
                        memberId,
                        crewId,
                        CrewScheduleDtos.month(month),
                        CrewScheduleDtos.day(date)));
    }

    private CrewScheduleDtos.CrewRunDetailResponse response(CrewRunDetailView view) {
        Map<Long, String> faces =
                avatarQuery.faceKeysOf(
                        view.attendees().stream()
                                .map(CrewRunDetailView.Attendee::memberId)
                                .filter(Objects::nonNull)
                                .toList());
        return CrewScheduleDtos.CrewRunDetailResponse.of(view, faces);
    }
}
