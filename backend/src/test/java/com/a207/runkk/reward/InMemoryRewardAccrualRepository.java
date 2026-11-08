package com.a207.runkk.reward;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** 시험용 적립 내역 저장소(14장 단위: 인메모리 Repository). (회원, 사유, 근거) 고유 제약을 흉내 내고 호출 횟수를 센다. */
public final class InMemoryRewardAccrualRepository implements RewardAccrualRepository {

    private final List<RewardAccrual> rows = new ArrayList<>();
    private int sumOnCalls;
    private final List<LocalDate> sumOnDays = new ArrayList<>();

    @Override
    public synchronized int amountFor(long memberId, AccrualReason reason, String sourceId) {
        return rows.stream()
                .filter(a -> a.memberId() == memberId && a.isFor(reason, sourceId))
                .mapToInt(RewardAccrual::amount)
                .sum();
    }

    @Override
    public synchronized int sumOn(long memberId, LocalDate day) {
        sumOnCalls++;
        sumOnDays.add(day);
        return rows.stream()
                .filter(a -> a.memberId() == memberId && a.accruedAt().toLocalDate().equals(day))
                .filter(a -> a.reason() != AccrualReason.EVENT)
                .mapToInt(RewardAccrual::amount)
                .sum();
    }

    @Override
    public synchronized void save(RewardAccrual a) {
        if (amountFor(a.memberId(), a.reason(), a.sourceId()) > 0) {
            throw new org.springframework.dao.DataIntegrityViolationException("uq_reward_accrual");
        }
        rows.add(a);
    }

    /** 시험 준비: 지갑을 거치지 않고 지난 날짜 적립 행을 넣는다(배포 전 자료). */
    public synchronized void seed(RewardAccrual a) {
        rows.add(a);
    }

    public synchronized int sumOnCalls() {
        return sumOnCalls;
    }

    public synchronized List<LocalDate> sumOnDays() {
        return List.copyOf(sumOnDays);
    }

    public synchronized void deleteByMember(long memberId) {
        rows.removeIf(a -> a.memberId() == memberId);
    }
}
