package com.a207.runkk.runimal;

import java.util.List;

/**
 * 16유형 목록(설계 5.6.2, FR-226, D-245).
 *
 * @param current 지금 확정 유형(확정받은 적이 없으면 null)
 * @param types 16유형과 확정받은 적이 있는지
 */
public record RunimalTypeCatalog(RunimalType current, List<Entry> types) {

    /**
     * 목록 한 칸.
     *
     * @param type 유형
     * @param everConfirmed 이 러너가 한 번이라도 확정받았는가(거짓이면 흐리게 보인다)
     */
    public record Entry(RunimalType type, boolean everConfirmed) {}
}
