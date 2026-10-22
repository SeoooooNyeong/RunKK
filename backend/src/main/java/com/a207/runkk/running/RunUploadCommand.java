package com.a207.runkk.running;

/**
 * POST /runs 요청(설계 6.1.3 RunUploadService.upload, D47). web.run이 요청 DTO에서 만든다.
 *
 * @param upload 완료 러닝 기록
 */
public record RunUploadCommand(RunUpload upload) {

    public RunUploadCommand {
        if (upload == null) {
            throw new IllegalArgumentException("러닝 기록이 비었다");
        }
    }

    /** RunRecord.receive의 입력(A-04 cmd.toUpload()). */
    public RunUpload toUpload() {
        return upload;
    }
}
