package com.a207.runkk.member;

import java.util.Optional;

/**
 * 배치고사 진행 저장소 포트(05-21 2.1). 구현은 infra.member.JpaPlacementProgressRepository이고 member 밖에서 쓰지 않는다.
 * 행은 lockOrCreate만 만든다(D-516).
 */
public interface PlacementProgressRepository {

    Optional<PlacementProgress> findById(long memberId);

    /**
     * INSERT … ON CONFLICT DO NOTHING 뒤 PESSIMISTIC_WRITE로 읽는다. PlacementService.lockForMatch와, 커밋
     * 전 매칭 INSERT와 직렬화하려는 탈퇴 두 경로(WithdrawalService, 같은 트랜잭션에서 deleteByMemberId)가 쓴다(D-478, 코드 리뷰
     * PL1-B3).
     */
    PlacementProgress lockOrCreate(long memberId);

    /** PESSIMISTIC_WRITE로 읽는다. 없으면 만들지 않고 비어 있다(countRound·correctVerdict, D-516). */
    Optional<PlacementProgress> findByIdForUpdate(long memberId);

    PlacementProgress save(PlacementProgress progress);

    /** 그 회원의 진행 행과 판 행을 지운다(탈퇴, D-489). 없으면 아무것도 하지 않는다. */
    void deleteByMemberId(long memberId);

    /** 그 회원이 그 대결을 판으로 셌는가. */
    boolean existsRound(long memberId, long battleId);
}
