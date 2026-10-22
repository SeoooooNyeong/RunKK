package com.a207.runkk.member;

/**
 * 동의 항목(자료사전 동의항목 8개, 6.1.2). 앞 4개는 필수다. HEART_RATE·FACE_TRANSFER는 가입 때 받지 않고 사용 시점에 받는다(N-23). 철회할
 * 수 있는 것은 LOCATION과 선택 동의 4개다(FR-013~FR-016, FR-025, Q-74).
 */
public enum ConsentItem {
    AGE_14_PLUS(true, true, false),
    TERMS(true, true, false),
    PRIVACY(true, true, false),
    LOCATION(true, true, true),
    MARKETING(false, true, true),
    HEART_RATE(false, false, true),
    FACE_TRANSFER(false, false, true),
    LLM_TRANSFER(false, true, true);

    private final boolean required;
    private final boolean collectedAtSignUp;
    private final boolean withdrawable;

    ConsentItem(boolean required, boolean collectedAtSignUp, boolean withdrawable) {
        this.required = required;
        this.collectedAtSignUp = collectedAtSignUp;
        this.withdrawable = withdrawable;
    }

    public boolean isRequired() {
        return required;
    }

    public boolean collectedAtSignUp() {
        return collectedAtSignUp;
    }

    /** 철회할 수 있는가. 약관·개인정보·만 14세 확인은 철회할 수 없다. */
    public boolean isWithdrawable() {
        return withdrawable;
    }
}
