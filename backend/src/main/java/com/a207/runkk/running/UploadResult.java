package com.a207.runkk.running;

/**
 * 러닝 기록 업로드 결과(SQ-02): 전송완료 / 이미 저장됨. 앱은 둘 다 성공으로 보고 기기 보관함에서 지운다(FR-040).
 *
 * @param recordId 기록 ID
 * @param alreadyStored 같은 기록 ID가 이미 있었는가
 * @param valid 유효 러닝인가(이미 저장됨이면 false)
 */
public record UploadResult(String recordId, boolean alreadyStored, boolean valid) {

    public static UploadResult stored(String recordId, boolean valid) {
        return new UploadResult(recordId, false, valid);
    }

    public static UploadResult alreadyStored(String recordId) {
        return new UploadResult(recordId, true, false);
    }
}
