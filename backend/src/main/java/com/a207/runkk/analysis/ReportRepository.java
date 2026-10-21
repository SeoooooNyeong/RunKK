package com.a207.runkk.analysis;

import java.util.Optional;

/**
 * Report 저장 포트(설계 6.1.4). 구현 infra.analysis.JpaReportRepository. analysis 밖에서 쓰지 않는다(13.1 규칙 4).
 */
public interface ReportRepository {

    Optional<Report> findByMember(long memberId);

    void save(Report report);

    void deleteByMember(long memberId);
}
