package com.a207.runkk.app.ui.share

import android.content.ActivityNotFoundException
import java.io.IOException
import timber.log.Timber

internal const val PNG = "image/png"

/**
 * 공유·기기 저장에서 예상되는 실패(권한·저장 공간·받을 앱 없음·주소 오류·지원하지 않는 제공자)만 잡아 원인을 기록하고 null로 돌려준다(리뷰 N-7).
 * 그 밖의 예외(프로그래밍 오류)는 그대로 올린다.
 */
internal fun <T> shareFailureOrNull(what: String, block: () -> T): T? = try {
    block()
} catch (e: SecurityException) {
    logged(e, what)
} catch (e: IOException) {
    logged(e, what)
} catch (e: ActivityNotFoundException) {
    logged(e, what)
} catch (e: IllegalArgumentException) {
    logged(e, what)
} catch (e: IllegalStateException) {
    logged(e, what)
} catch (e: UnsupportedOperationException) {
    // 쓰기·읽기를 지원하지 않는 content 제공자.
    logged(e, what)
}

private fun logged(e: Exception, what: String): Nothing? {
    Timber.w(e, what)
    return null
}
