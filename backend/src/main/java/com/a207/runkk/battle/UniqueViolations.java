package com.a207.runkk.battle;

import java.sql.SQLException;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * 무결성 위반이 고유 제약 위반(SQLSTATE 23505)인지 가른다. 경합으로 보고 다시 시도하거나 사유로 바꿀 수 있는 것은 고유 제약 위반뿐이고, NOT
 * NULL·CHECK·길이 위반은 그대로 올린다(코드 리뷰 3차 B-L3, 8차 BE8-5). 제약 이름은 H2 create-drop이 PostgreSQL과 달라
 * SQLSTATE로 본다.
 */
final class UniqueViolations {

    private static final String UNIQUE_VIOLATION = "23505";

    private UniqueViolations() {}

    static boolean isUniqueViolation(DataIntegrityViolationException e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && sql.getSQLState() != null) {
                return UNIQUE_VIOLATION.equals(sql.getSQLState());
            }
        }
        return false;
    }
}
