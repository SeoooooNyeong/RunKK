package com.a207.runkk.common;

/** 기록 공개 범위(설계 6.1.1, 6.4). 상수별 메소드로 공개 범위 분기를 없앴다. */
public enum RecordVisibility {
    PUBLIC {
        @Override
        public boolean allows(ViewerRelation r) {
            return true;
        }

        @Override
        public boolean includedInPersonalRanking() {
            return true;
        }

        @Override
        public boolean ghostEligible() {
            return true;
        }
    },
    CREW {
        @Override
        public boolean allows(ViewerRelation r) {
            return r == ViewerRelation.OWNER || r == ViewerRelation.SAME_CREW;
        }

        @Override
        public boolean includedInPersonalRanking() {
            return true;
        }

        @Override
        public boolean ghostEligible() {
            return false;
        }
    },
    PRIVATE {
        @Override
        public boolean allows(ViewerRelation r) {
            return r == ViewerRelation.OWNER;
        }

        @Override
        public boolean includedInPersonalRanking() {
            return false;
        }

        @Override
        public boolean ghostEligible() {
            return false;
        }
    };

    /** 열람자 관계로 기록을 볼 수 있는가(FR-060). */
    public abstract boolean allows(ViewerRelation r);

    /** 개인 랭킹에 넣는가(FR-070). */
    public abstract boolean includedInPersonalRanking();

    /** 고스트 후보가 될 수 있는가(FR-095). */
    public abstract boolean ghostEligible();
}
