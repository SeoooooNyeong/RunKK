package com.a207.runkk.aiprivacy;

/** AI가 소유한 분석·대화·아바타 자료를 회원별로 멱등 파기한다. */
public interface AiDataEraser {
    void erase(long memberId);
}
