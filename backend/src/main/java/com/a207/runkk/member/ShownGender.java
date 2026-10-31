package com.a207.runkk.member;

/**
 * 다른 러너에게 보이는 회원 성별(표시 성별, 설계 D-327·D-336, FR-162·FR-233). 성별이 남·여이고 성별 공개(PUBLIC)면 그 값,
 * 아니면(비공개·미입력·밝히지 않음) 회원 ID에서 정해지는 고정 대체값이다. 대체값은 실제 성별과 무관하고 저장 칼럼이 없으며 볼 때마다 같아서, 같은 회원이
 * 랭킹·프로필·대결에서 같은 그림으로 보이고 비공개임이 드러나지 않는다. 가상 상대(회원 아님)는 여기를 쓰지 않는다.
 */
public final class ShownGender {

    private ShownGender() {}

    /** 회원 성별과 성별 공개 값으로 정한 표시 성별(남·여 중 하나). */
    static Gender of(long memberId, Gender gender, ProfileVisibility visibility) {
        boolean known = gender == Gender.MALE || gender == Gender.FEMALE;
        return known && visibility == ProfileVisibility.PUBLIC ? gender : fallbackOf(memberId);
    }

    /** 회원 ID를 섞어(SplitMix64 마무리 함수) 한 비트로 남·여를 고른다. 같은 ID는 늘 같은 값이다. */
    public static Gender fallbackOf(long memberId) {
        long z = memberId + 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z = z ^ (z >>> 31);
        return (z & 1L) == 0L ? Gender.MALE : Gender.FEMALE;
    }
}
