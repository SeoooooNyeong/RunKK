package com.a207.runkk.avatar;

import java.time.LocalDate;

/**
 * 회원별 하루 셀카 변환 실패 횟수 포트(B4 리뷰: 실패는 생성 횟수를 쓰지 않아 ai를 끝없이 부를 수 있었다). 구현은
 * infra.avatar.RedisSelfieFailureStore(INCR + 만료 48시간). 설계 6.1.8 포트 표에 없어 더한 포트다.
 */
public interface SelfieFailureStore {

    /** 그날 실패 횟수. */
    int failuresOn(long memberId, LocalDate day);

    /** 그날 실패 한 번을 더한다. */
    void recordFailure(long memberId, LocalDate day);
}
