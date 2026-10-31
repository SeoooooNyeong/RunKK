package com.a207.runkk.web.runimal;

import static org.assertj.core.api.Assertions.assertThat;

import com.a207.runkk.runimal.AxisLean;
import com.a207.runkk.runimal.RunimalAxis;
import com.a207.runkk.runimal.RunimalCardView;
import com.a207.runkk.runimal.RunimalType;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Runimal 카드 응답의 축 항목")
class RunimalCardResponseTest {

    @Test
    @DisplayName("축 항목마다 우세 쪽 lean(A·B)을 싣고 기존 필드는 그대로 둔다")
    void axesCarryLean() {
        RunimalCardView card =
                new RunimalCardView(
                        RunimalType.LONE_WOLF,
                        "외로운 늑대",
                        "WOLF",
                        "설명",
                        false,
                        List.of(
                                new RunimalCardView.AxisBar(
                                        RunimalAxis.COMPANION, AxisLean.A, "솔로런", 75),
                                new RunimalCardView.AxisBar(
                                        RunimalAxis.COURSE, AxisLean.B, "탐험", 60)));

        RunimalController.CardResponse response = RunimalController.CardResponse.of(card);

        assertThat(response.axes())
                .extracting(
                        RunimalController.AxisResponse::axis,
                        RunimalController.AxisResponse::label,
                        RunimalController.AxisResponse::percent,
                        RunimalController.AxisResponse::lean)
                .containsExactly(
                        org.assertj.core.api.Assertions.tuple("COMPANION", "솔로런", 75, "A"),
                        org.assertj.core.api.Assertions.tuple("COURSE", "탐험", 60, "B"));
    }
}
