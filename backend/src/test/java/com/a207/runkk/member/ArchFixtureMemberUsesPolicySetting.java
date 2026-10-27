package com.a207.runkk.member;

import com.a207.runkk.policy.SettingKey;

/** ArchUnit 음성 시험 전용(규칙 11 위반, D-133). member는 policy에서 TierBoundary·OperationPolicy만 쓴다. */
public class ArchFixtureMemberUsesPolicySetting {
    public String name(SettingKey key) {
        return key.name();
    }
}
