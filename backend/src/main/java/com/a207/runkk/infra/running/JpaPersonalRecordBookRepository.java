package com.a207.runkk.infra.running;

import com.a207.runkk.running.PersonalRecordBook;
import com.a207.runkk.running.PersonalRecordBookRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;

/**
 * running.PersonalRecordBookRepository의 JPA 구현(설계 5.1.1 infra.running).
 * personal_record_book·personal_best 테이블만 읽고 쓴다. 행 잠금은 "없으면 넣기(INSERT … ON CONFLICT DO NOTHING) →
 * SELECT … FOR UPDATE"로 해 첫 업로드가 동시에 와도 한쪽이 기다린다(5.3).
 */
@Repository
public class JpaPersonalRecordBookRepository implements PersonalRecordBookRepository {

    private final EntityManager entityManager;

    public JpaPersonalRecordBookRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public PersonalRecordBook findOrCreateForUpdate(long memberId) {
        entityManager
                .createNativeQuery(
                        "insert into personal_record_book (member_id) values (:m) on conflict do"
                                + " nothing")
                .setParameter("m", memberId)
                .executeUpdate();
        return entityManager.find(
                PersonalRecordBook.class, memberId, LockModeType.PESSIMISTIC_WRITE);
    }

    @Override
    public PersonalRecordBook findOrCreate(long memberId) {
        PersonalRecordBook book = entityManager.find(PersonalRecordBook.class, memberId);
        return book == null ? PersonalRecordBook.of(memberId) : book;
    }

    @Override
    public void save(PersonalRecordBook book) {
        if (!entityManager.contains(book)) {
            entityManager.merge(book);
        }
    }

    @Override
    public void deleteByMember(long memberId) {
        PersonalRecordBook book = entityManager.find(PersonalRecordBook.class, memberId);
        if (book != null) {
            entityManager.remove(book);
        }
    }
}
