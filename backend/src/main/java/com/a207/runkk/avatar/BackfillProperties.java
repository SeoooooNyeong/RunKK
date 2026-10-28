package com.a207.runkk.avatar;

import java.time.LocalDateTime;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 배경 해금 조회 채우기 설정(D-176). 이 시각 이전에 저장한 기록의 거리만 알림 없이 채운다(배포 전에 넘은 랜드마크). 그 뒤 거리는 리스너·보정 배치가 알림과 함께
 * 해금한다. 값은 V25(배경 해금) 적용 시각으로 둔다.
 *
 * @param backfillCutoff 채우기 기준 시각(서버 시간대)
 */
@ConfigurationProperties(prefix = "runkk.avatar")
public record BackfillProperties(LocalDateTime backfillCutoff) {

    public BackfillProperties {
        if (backfillCutoff == null) {
            throw new IllegalArgumentException("runkk.avatar.backfill-cutoff가 비었다");
        }
    }
}
