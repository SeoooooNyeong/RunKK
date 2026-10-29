package com.a207.runkk.web.runimal;

import com.a207.runkk.common.ServerZone;
import com.a207.runkk.runimal.RunimalAxis;
import com.a207.runkk.runimal.RunimalCardView;
import com.a207.runkk.runimal.RunimalService;
import com.a207.runkk.runimal.RunimalStatusView;
import com.a207.runkk.runimal.RunimalType;
import com.a207.runkk.runimal.RunimalTypeCatalog;
import com.a207.runkk.runimal.RunimalTypeDetail;
import com.a207.runkk.runimal.RunimalTypeService;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
    private final RunimalTypeService runimalTypeService;

    RunimalController(RunimalService runimalService, RunimalTypeService runimalTypeService) {
        this.runimalService = runimalService;
        this.runimalTypeService = runimalTypeService;
    }

    /** 16유형 목록(FR-226, v0.14 D-245). 한 번도 확정받지 못한 유형은 everConfirmed가 거짓이다(앱이 흐리게 보인다). */
    @GetMapping("/types")
    TypesResponse types(@RequestAttribute(MEMBER_ID) long memberId) {
        RunimalTypeCatalog catalog = runimalTypeService.catalog(memberId);
        return new TypesResponse(
                catalog.current() == null ? null : catalog.current().name(),
                catalog.types().stream()
                        .map(e -> TypeEntry.of(e.type(), e.everConfirmed()))
                        .toList());
    }

    /** 유형 상세(FR-226, D-246). 모르는 유형은 400. 비율은 매일 0시 스냅숏이고 확정자가 없으면 null이다. */
    @GetMapping("/types/{type}")
    TypeDetailResponse type(@PathVariable("type") String type) {
        RunimalType known = RunimalType.valueOf(type); // 모르는 이름은 IllegalArgumentException → 400
        RunimalTypeDetail detail = runimalTypeService.detail(known);
        return new TypeDetailResponse(
                known.name(),
                known.displayName(),
                known.icon(),
                known.description(),
                axesOf(known),
                detail.traits(),
                detail.sharePercent(),
                detail.computedAt() == null
                        ? null
                        : detail.computedAt().atZone(ServerZone.ID).toOffsetDateTime());
    }

    /** 4축 성향(축 이름 → A·B). */
    private static Map<String, String> axesOf(RunimalType type) {
        Map<String, String> axes = new LinkedHashMap<>();
        for (RunimalAxis axis : RunimalAxis.values()) {
            axes.put(axis.name(), type.lean(axis).name());
        }
        return axes;
    }

    /**
     * 16유형 목록.
     *
     * @param current 지금 확정 유형(확정받은 적이 없으면 null)
     * @param types 16유형
     */
    record TypesResponse(String current, List<TypeEntry> types) {}

    /**
     * 목록 한 칸.
     *
     * @param type 유형 이름
     * @param displayName 유형명
     * @param icon 아이콘(동물 이름)
     * @param description 한 줄 설명
     * @param axes 4축 성향(COMPANION·DISTANCE·COURSE·HABIT → A·B)
     * @param everConfirmed 이 러너가 한 번이라도 확정받았는가
     */
    record TypeEntry(
            String type,
            String displayName,
            String icon,
            String description,
            Map<String, String> axes,
            boolean everConfirmed) {

        static TypeEntry of(RunimalType t, boolean everConfirmed) {
            return new TypeEntry(
                    t.name(), t.displayName(), t.icon(), t.description(), axesOf(t), everConfirmed);
        }
    }

    /**
     * 유형 상세.
     *
     * @param type 유형 이름
     * @param displayName 유형명
     * @param icon 아이콘
     * @param description 한 줄 설명
     * @param axes 4축 성향
     * @param traits 특징 3줄
     * @param sharePercent 러너 중 이 유형 비율(%), 확정자가 없거나 계산 전이면 null
     * @param computedAt 비율 계산 시각(+09:00), 계산 전이면 null
     */
    record TypeDetailResponse(
            String type,
            String displayName,
            String icon,
            String description,
            Map<String, String> axes,
            List<String> traits,
            Integer sharePercent,
            OffsetDateTime computedAt) {}

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
