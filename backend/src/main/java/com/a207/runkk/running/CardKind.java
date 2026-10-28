package com.a207.runkk.running;

/**
 * 러닝 카드 앞면 라벨(자료사전 카드종류 = 자유런 | 배틀런 | 셀프런 | 셀럽런 | 크루런, 1.5.3). 모드만으로 정해지지 않아 기록에 따로 저장한다. 모드와의 일관성:
 * FREE ↔ FREE_RUN, CREW_RUN ↔ CREW_RUN, BATTLE ↔ 나머지 셋.
 */
public enum CardKind {
    FREE_RUN(RunMode.FREE),
    BATTLE_RUN(RunMode.BATTLE),
    SELF_RUN(RunMode.BATTLE),
    CELEB_RUN(RunMode.BATTLE),
    CREW_RUN(RunMode.CREW_RUN);

    private final RunMode mode;

    CardKind(RunMode mode) {
        this.mode = mode;
    }

    /** 이 카드 종류가 그 모드의 기록에 붙을 수 있는가. */
    public boolean belongsTo(RunMode runMode) {
        return mode == runMode;
    }
}
