package com.a207.runkk.web.admin.setting;

import com.a207.runkk.policy.ArchFixturePolicyRepository;

/** ArchUnit 음성 시험 전용(규칙 4 위반). web.admin.setting → policy는 허용이지만 Repository 직접 사용은 금지. */
public class ArchFixtureSettingUsesRepository {
    private final ArchFixturePolicyRepository repository;

    public ArchFixtureSettingUsesRepository(ArchFixturePolicyRepository repository) {
        this.repository = repository;
    }

    public void touch() {
        repository.save("x");
    }
}
