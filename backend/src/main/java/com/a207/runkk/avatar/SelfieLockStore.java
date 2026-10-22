package com.a207.runkk.avatar;

import java.time.Duration;
import java.util.Optional;

/**
 * 회원별 셀카 변환 진행 중 잠금 포트(설계 5.3 "같은 회원의 셀카 변환 동시 요청": Redis SET NX, 키 회원, 30초). 잡지 못하면 "이미 변환 중"이고 외부
 * 얼굴 API를 두 번 부르지 않는다. 구현은 infra.avatar.RedisSelfieLockStore. 설계 6.1.8 포트 표에 없어 더한 포트다.
 */
public interface SelfieLockStore {

    /** 없을 때만 잡고 잠금 표(토큰)를 돌려준다. 이미 잡혀 있으면 비어 있다. ttl이 지나면 저절로 풀린다. */
    Optional<String> tryLock(long memberId, Duration ttl);

    /** 그 토큰으로 잡은 잠금일 때만 푼다(ttl이 지나 다른 요청이 잡은 잠금을 지우지 않는다). */
    void unlock(long memberId, String token);
}
