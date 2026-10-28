package com.a207.runkk.crew;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 투표 선택지 하나(FR-206). 표 crew_poll_option(PK(post_id, option_no)). 만든 뒤 바뀌지 않는다.
 *
 * @param optionNo 번호(1~5)
 * @param label 이름(≤ 40자, 코드 포인트)
 */
@Embeddable
public record PollOption(
        @Column(name = "option_no", nullable = false) @JdbcTypeCode(SqlTypes.SMALLINT) int optionNo,
        @Column(
                        name = "label",
                        nullable = false,
                        length = PollDraft.MAX_LABEL * CrewPost.UTF16_UNITS_PER_CHAR)
                String label) {}
