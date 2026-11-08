package com.a207.runkk.runbody;

/** ai가 받지 않을 요청(409·413·422) 또는 요청을 만들 수 없음. 재제출하지 않는다(D-450). 원격 본문은 담지 않는다. */
public class RunBodyRejectedException extends RuntimeException {

    public RunBodyRejectedException(String reason) {
        super(reason);
    }
}
