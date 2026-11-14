package com.a207.runkk.running;

import com.a207.runkk.common.AvatarLook;
import java.util.Optional;

/**
 * 회원의 지금 아바타 모습을 읽는 포트(설계 5.31 B, [임시:D-성-248]). 구현은 avatar.AvatarLookReader. running과 battle은
 * avatar를 import할 수 없어 이 포트로 읽는다(WeatherGateway와 같은 방식).
 */
public interface AvatarLookSource {

    /** 회원의 지금 모습. 읽을 모습이 없으면 비어 있다. */
    Optional<AvatarLook> currentLookOf(long memberId);
}
