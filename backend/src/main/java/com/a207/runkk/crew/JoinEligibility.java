package com.a207.runkk.crew;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.ReasonCode;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** 크루 가입·생성 자격(설계 6.1.7 값, srs.md 6.3 5.3.1 "자격 판정" 표, FR-124). 불변이다. */
public final class JoinEligibility {

    /** 판정. */
    public enum Result {
        ELIGIBLE,
        ALREADY_MEMBER,
        COOLDOWN
    }

    private static final JoinEligibility ELIGIBLE = new JoinEligibility(Result.ELIGIBLE, null);
    private static final JoinEligibility ALREADY_MEMBER =
            new JoinEligibility(Result.ALREADY_MEMBER, null);

    private final Result result;
    private final LocalDateTime availableAt;

    private JoinEligibility(Result result, LocalDateTime availableAt) {
        this.result = result;
        this.availableAt = availableAt;
    }

    /**
     * 소속 중 행이 있으면 ALREADY_MEMBER, 가장 최근 행의 cooldownUntil &gt; now면 COOLDOWN(availableAt), 아니면
     * ELIGIBLE. 크루 삭제로 없어진 소속은 행이 지워져 쿨타임이 생기지 않는다.
     */
    public static JoinEligibility evaluate(List<CrewMembership> history, LocalDateTime now) {
        if (history.stream().anyMatch(CrewMembership::isActive)) {
            return ALREADY_MEMBER;
        }
        return history.stream()
                .max(Comparator.comparing(CrewMembership::leftAtOrJoinedAt))
                .flatMap(CrewMembership::cooldownUntil)
                .filter(now::isBefore)
                .map(until -> new JoinEligibility(Result.COOLDOWN, until))
                .orElse(ELIGIBLE);
    }

    public Result result() {
        return result;
    }

    public boolean isEligible() {
        return result == Result.ELIGIBLE;
    }

    /** 쿨타임이면 가입할 수 있는 시각. */
    public Optional<LocalDateTime> availableAt() {
        return Optional.ofNullable(availableAt);
    }

    /** 자격이 없으면 ALREADY_IN_CREW 또는 COOLDOWN. */
    public void require() {
        if (result == Result.ALREADY_MEMBER) {
            throw new BusinessRuleException(ReasonCode.ALREADY_IN_CREW);
        }
        if (result == Result.COOLDOWN) {
            throw new BusinessRuleException(ReasonCode.COOLDOWN);
        }
    }
}
