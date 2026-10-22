package com.a207.runkk.running;

/** 최고 기록 장부 저장 포트(설계 6.1.3). 구현은 infra.running.JpaPersonalRecordBookRepository. */
public interface PersonalRecordBookRepository {

    /** 행이 없으면 만들고 SELECT … FOR UPDATE로 잠근다. 같은 회원의 업로드를 직렬화한다(5.3). */
    PersonalRecordBook findOrCreateForUpdate(long memberId);

    /** 잠그지 않고 읽는다. 행이 없으면 저장하지 않은 빈 장부. */
    PersonalRecordBook findOrCreate(long memberId);

    void save(PersonalRecordBook book);

    void deleteByMember(long memberId);
}
