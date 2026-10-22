package com.a207.runkk.running;

import java.util.List;
import java.util.Optional;

/**
 * 파기 지시 저장 포트(설계 6.1.3). 구현은 infra.running.JpaDisposalOrderRepository. deleteByMember는 탈퇴 수신자가 회원의
 * 지시를 지우려고 더했다(설계 6.1.3 RunWithdrawalListener, B2 결정).
 */
public interface DisposalOrderRepository {

    void save(DisposalOrder order);

    List<DisposalOrder> findScheduled();

    /** 기기가 아직 확인하지 않은 그 회원의 지시. */
    List<DisposalOrder> findNotConfirmedByMember(long memberId);

    Optional<DisposalOrder> findById(long id);

    void deleteByMember(long memberId);
}
