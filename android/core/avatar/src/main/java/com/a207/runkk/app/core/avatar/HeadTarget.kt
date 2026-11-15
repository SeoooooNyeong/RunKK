package com.a207.runkk.app.core.avatar

/**
 * 생성 머리를 놓을 몸체 기준(1024 캔버스): 턱 중심 x, 턱 y, 정수리 y. 몸체(남녀·정면/측면)의 값이며 특정 인물의 좌표가 아니다.
 * 서버 분리·정합 오류를 이 값이나 파츠별 오프셋으로 덮지 않는다(온보딩 4절).
 */
internal enum class HeadTarget(val centerX: Float, val chinY: Float, val crownY: Float) {
    FEMALE_FRONT(534f, 794f, 102f),
    MALE_FRONT(520f, 854f, 59f),
    FEMALE_SIDE(651f, 798f, 102f),
    MALE_SIDE(582f, 858f, 60f);

    companion object {
        fun front(gender: AvatarGender): HeadTarget = when (gender) {
            AvatarGender.FEMALE -> FEMALE_FRONT
            AvatarGender.MALE -> MALE_FRONT
        }

        fun side(gender: AvatarGender): HeadTarget = when (gender) {
            AvatarGender.FEMALE -> FEMALE_SIDE
            AvatarGender.MALE -> MALE_SIDE
        }
    }
}
