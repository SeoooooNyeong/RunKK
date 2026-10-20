package com.a207.runkk.app.core.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** 화면 경로 이름(5.1.2). 화면 사이 이동은 이 이름으로만 한다. */
object Routes {
    const val HOME = "home"

    /** Navigation 3 백 스택에 올리는 경로 키. 경로 이름 문자열 하나를 담는다. */
    @Serializable
    data class Key(val route: String) : NavKey
}
