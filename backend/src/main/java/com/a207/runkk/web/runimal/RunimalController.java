package com.a207.runkk.web.runimal;

import com.a207.runkk.runimal.RunimalCardView;
import com.a207.runkk.runimal.RunimalService;
import com.a207.runkk.runimal.RunimalStatusView;
import com.a207.runkk.runimal.RunimalType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Runimal 조회(설계 5.7.1: GET /runimal, GET /runimal/card → RunimalService.status, shareCard,
 * FR-151~FR-154, FR-168). 유형이 없을 때의 카드 요청은 409 + NO_TYPE이다.
 */
@RestController
@RequestMapping("/api/v1/runimal")
public class RunimalController {

    private static final String MEMBER_ID = "memberId";
    private static final double PERCENT = 100.0;

    private final RunimalService runimalService;

    RunimalController(RunimalService runimalService) {
        this.runimalService = runimalService;
    }

    @GetMapping
    StatusResponse status(@RequestAttribute(MEMBER_ID) long memberId) {
        RunimalStatusView view = runimalService.status(memberId);
        RunimalType current =
                view.confirmedType() != null ? view.confirmedType() : view.provisionalType();
        return new StatusResponse(
                current == null ? null : current.name(),
                view.confirmedType() == null && view.provisionalType() != null,
                view.validRunCount(),
                view.remainingRuns(),
                view.cardValue().map(CardResponse::of).orElse(null),
                view.allTypes().stream().map(Enum::name).toList());
    }

    @GetMapping("/card")
    CardResponse card(@RequestAttribute(MEMBER_ID) long memberId) {
        return CardResponse.of(runimalService.shareCard(memberId));
    }

    /**
     * 유형 현황.
     *
     * @param type 현재 유형(확정 우선, 없으면 임시), 없으면 null
     * @param provisional 임시 유형인가
     * @param validRunCount 유효 러닝 횟수
     * @param remainingRuns 다음 판정까지 남은 횟수
     * @param card 유형 카드
     * @param allTypes 16유형
     */
    record StatusResponse(
            String type,
            boolean provisional,
            int validRunCount,
            int remainingRuns,
            CardResponse card,
            List<String> allTypes) {}

    /**
     * 유형 카드.
     *
     * @param type 유형
     * @param title 유형명
     * @param icon 아이콘(동물 이름)
     * @param description 한 줄 설명
     * @param provisional 임시 유형
     * @param axisRatios 축 이름 → 우세 성향 비율(0~1)
     * @param axes 축 막대(성향 이름·비율)
     */
    record CardResponse(
            String type,
            String title,
            String icon,
            String description,
            boolean provisional,
            Map<String, Double> axisRatios,
            List<AxisResponse> axes) {

        static CardResponse of(RunimalCardView card) {
            Map<String, Double> ratios = new LinkedHashMap<>();
            card.axes().forEach(bar -> ratios.put(bar.axis().name(), bar.percent() / PERCENT));
            return new CardResponse(
                    card.type().name(),
                    card.title(),
                    card.icon(),
                    card.description(),
                    card.provisional(),
                    ratios,
                    card.axes().stream()
                            .map(
                                    bar ->
                                            new AxisResponse(
                                                    bar.axis().name(), bar.label(), bar.percent()))
                            .toList());
        }
    }

    /**
     * 축 막대 하나("솔로런 78%").
     *
     * @param axis 축
     * @param label 우세 성향 이름
     * @param percent 비율(%)
     */
    record AxisResponse(String axis, String label, int percent) {}
}
