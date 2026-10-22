package com.a207.runkk.infra.audit;

import com.a207.runkk.policy.ArchFixturePolicyRepository;

/** ArchUnit 음성 시험 전용(규칙 1·4 위반). infra.audit → policy는 12.2 의존 목록에 없다. */
public class ArchFixtureInfraAuditUsesPolicy {
    private final ArchFixturePolicyRepository repository;

    public ArchFixtureInfraAuditUsesPolicy(ArchFixturePolicyRepository repository) {
        this.repository = repository;
    }

    public void touch() {
        repository.save("x");
    }
}
