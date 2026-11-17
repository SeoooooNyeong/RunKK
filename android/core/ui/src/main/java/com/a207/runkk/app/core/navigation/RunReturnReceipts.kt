package com.a207.runkk.app.core.navigation

import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 복귀 값 보관(탐색 소유자 RunkkNavHost가 하나 만들어 [LocalRunReturnReceipts]로 내려 준다, 설계 05-32 4.4). 결과 화면이 넣고 홈·크루 홈이
 * 다음에 보일 때 한 번씩 꺼낸다. 화면 모듈끼리 직접 보지 않고 전역 싱글턴 dirty 값도 두지 않는다. 메모리에만 두며 앱을 다시 켜면
 * 비어 있다(목적지는 원본을 다시 읽는다). Main 스레드에서만 쓴다.
 */
@Stable
class RunReturnReceipts {
    private var home: HomeRunReturnReceipt? = null
    private var crew: CrewRunReturnReceipt? = null

    fun deliver(receipt: CrewRunReturnReceipt) {
        home = receipt.forHome()
        crew = receipt
    }

    /** 홈이 꺼낸다. 한 번만 준다. */
    fun takeHome(): HomeRunReturnReceipt? = home.also { home = null }

    /** 크루 홈이 꺼낸다. 한 번만 준다. */
    fun takeCrew(): CrewRunReturnReceipt? = crew.also { crew = null }
}

/** 복귀 값 보관. 앱이 넣지 않으면(미리보기·시험) null이다. */
val LocalRunReturnReceipts = staticCompositionLocalOf<RunReturnReceipts?> { null }
