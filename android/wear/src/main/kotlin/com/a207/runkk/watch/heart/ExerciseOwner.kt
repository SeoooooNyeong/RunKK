package com.a207.runkk.watch.heart

import kotlinx.coroutines.flow.Flow

/** 운동 세션의 주인(ExerciseTrackedStatus). 알 수 없음은 [NONE]으로 본다. */
enum class ExerciseOwner {
    NONE,
    THIS_APP,
    OTHER_APP
}
