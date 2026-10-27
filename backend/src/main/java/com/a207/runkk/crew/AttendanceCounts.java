package com.a207.runkk.crew;

import java.util.Collection;

/**
 * 일정의 참석·불참·미정 인원(FR-133).
 *
 * @param going 참석
 * @param notGoing 불참
 * @param undecided 미정
 */
public record AttendanceCounts(int going, int notGoing, int undecided) {

    /** 응답 목록을 센다. */
    static AttendanceCounts of(Collection<AttendanceAnswer> answers) {
        int going = 0;
        int notGoing = 0;
        int undecided = 0;
        for (AttendanceAnswer a : answers) {
            switch (a) {
                case GOING -> going++;
                case NOT_GOING -> notGoing++;
                case UNDECIDED -> undecided++;
            }
        }
        return new AttendanceCounts(going, notGoing, undecided);
    }
}
