package com.a207.runkk.avatar;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/** 아바타 저장소 포트(설계 6.1.8). 구현은 infra.avatar.JpaAvatarRepository. avatar 밖에서 쓰지 않는다. */
public interface AvatarRepository {

    /** 없으면 저장하지 않은 새 아바타(조회용). */
    Avatar findOrCreate(long memberId);

    /**
     * ID 묶음의 아바타(얼굴 부위·착용 포함)를 질의 하나로 읽는다. 행이 없는 회원은 빠지고 만들지 않는다(랭킹 줄 모습, S15P31A207-300). 순서는 정하지
     * 않는다.
     */
    List<Avatar> findAllByMemberIds(Collection<Long> memberIds);

    /** 없으면 만들고 행을 잠가(SELECT … FOR UPDATE) 돌려준다(5.3 아바타 하루 1회). 트랜잭션 안에서만 부른다. */
    Optional<Avatar> findForUpdate(long memberId);

    void save(Avatar avatar);

    /** 모든 아바타(0시 외형 재계산). 트랜잭션 안에서 소비한다. */
    Stream<Avatar> streamAll();

    void deleteByMember(long memberId);
}
