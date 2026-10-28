package com.a207.runkk.policy;

/** ArchUnit 음성 시험 전용(규칙 1·4). policy가 선언한 저장 포트 흉내. */
public interface ArchFixturePolicyRepository {
    void save(String value);
}
