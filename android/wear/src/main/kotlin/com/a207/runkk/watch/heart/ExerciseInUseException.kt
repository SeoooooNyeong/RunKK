package com.a207.runkk.watch.heart

import kotlinx.coroutines.flow.Flow

/** 다른 앱(삼성 헬스 등)이 운동 세션을 쓰는 중이라 열지 않았다. 열면 그 앱의 운동이 끝나 버린다. */
class ExerciseInUseException : IllegalStateException("다른 앱이 운동 세션을 쓰는 중")
