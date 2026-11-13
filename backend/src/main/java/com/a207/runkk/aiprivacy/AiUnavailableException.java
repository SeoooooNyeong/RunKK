package com.a207.runkk.aiprivacy;

/** 원격 본문·주소·토큰을 보관하지 않는 재시도용 실패. */
public class AiUnavailableException extends RuntimeException {
    public AiUnavailableException(String reason) {
        super(reason);
    }
}
