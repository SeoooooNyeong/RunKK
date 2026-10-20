package com.a207.runkk.notification;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 기기 토큰 저장 포트(설계 6.1.10, D-43). 구현은 infra.notification.JpaPushDeviceRepository. */
public interface PushDeviceRepository {

    void save(PushDevice device);

    Optional<PushDevice> findByToken(String token);

    List<String> tokensOf(long memberId);

    void deleteTokens(Collection<String> tokens);

    void deleteByMember(long memberId);
}
