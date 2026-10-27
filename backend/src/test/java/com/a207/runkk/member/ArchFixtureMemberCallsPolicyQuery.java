package com.a207.runkk.member;

import com.a207.runkk.policy.OperationPolicy;

/** ArchUnit 음성 시험 전용(규칙 11 위반, D-133). member는 OperationPolicy에서 tierBoundary() 조회만 부른다. */
public class ArchFixtureMemberCallsPolicyQuery {
    public boolean asks(OperationPolicy policy) {
        return policy.matchCondition() != null;
    }
}
