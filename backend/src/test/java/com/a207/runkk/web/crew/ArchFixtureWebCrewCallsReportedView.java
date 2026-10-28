package com.a207.runkk.web.crew;

import com.a207.runkk.crew.CrewPostService;

/** ArchUnit 음성 시험 전용(규칙 8 위반, TC-6206 ②). 신고용 공개 연산은 report.target만 부른다(D-135). */
public class ArchFixtureWebCrewCallsReportedView {
    public boolean exists(CrewPostService posts, long postId) {
        return posts.reportedView(postId).isPresent();
    }
}
