package com.a207.runkk.runbody;

/**
 * ai에 닿지 못했거나 일시 실패(503·시간 초과·알 수 없는 응답). connectionFailed면 연결 자체가 안 된 것이라 재제출 횟수에 세지 않는다(D-450).
 * 원격 본문·주소·토큰은 담지 않는다.
 */
public class RunBodyUnavailableException extends RuntimeException {

    private final boolean connectionFailed;

    public RunBodyUnavailableException(String reason, boolean connectionFailed) {
        super(reason);
        this.connectionFailed = connectionFailed;
    }

    public boolean connectionFailed() {
        return connectionFailed;
    }
}
