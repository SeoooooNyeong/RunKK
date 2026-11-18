package com.a207.runkk.web.record;

import static org.assertj.core.api.Assertions.assertThat;

import com.a207.runkk.common.Tier;
import com.a207.runkk.running.EvaluationView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 기록 상세 evaluation의 직전 티어([임시:D-성-972]): 재평가 기록은 바꾸기 전 티어, 과거 기록은 null. */
@DisplayName("RecordDtos.EvaluationDto: previousTier")
class EvaluationDtoTest {

    @Test
    void carriesPreviousTierName() {
        RecordDtos.EvaluationDto dto =
                RecordDtos.EvaluationDto.of(
                        new EvaluationView("REEVALUATION", Tier.GOLD, 20, null, Tier.SILVER));
        assertThat(dto.previousTier()).isEqualTo("SILVER");
        assertThat(dto.tier()).isEqualTo("GOLD");
    }

    @Test
    void oldRecordsHaveNoPreviousTier() {
        RecordDtos.EvaluationDto dto =
                RecordDtos.EvaluationDto.of(
                        new EvaluationView("REEVALUATION", Tier.GOLD, 10, "LONE_WOLF", null));
        assertThat(dto.previousTier()).isNull();
    }
}
