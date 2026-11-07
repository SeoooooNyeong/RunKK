package com.a207.runkk.web.runbody;

import com.a207.runkk.runbody.RunBodySessionView;
import com.fasterxml.jackson.annotation.JsonRawValue;

/** 런바디 응답(앱 계약 runbody contract.md v1.0 1절). */
public final class RunBodyDtos {

    private RunBodyDtos() {}

    /**
     * GET /api/v1/runbody/sessions/{recordId} 200 본문.
     *
     * @param recordId 경로의 기록 ID
     * @param status PENDING·ANALYZING·DONE·FAILED(서버 RunBodyStatus 이름 그대로)
     * @param result DONE일 때만 ai GET /sessions/{id} 200 본문 그대로, 아니면 null
     */
    public record RunBodySessionResponse(
            String recordId, String status, @JsonRawValue String result) {

        static RunBodySessionResponse of(RunBodySessionView view) {
            return new RunBodySessionResponse(view.recordId(), view.status().name(), view.result());
        }
    }
}
