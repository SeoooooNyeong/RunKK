package com.a207.runkk.app.core.avatar

import android.content.Context
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.awaitCancellation
import timber.log.Timber

/**
 * 홈 큰 아바타 Rive 첫 판 미리 데우기(시작 로딩 화면, S15P31A207-345 2차). 그 성별의 서기 Rive 파일을 가져오기 스레드에서 미리 가져와
 * 붙잡는다([RivFiles], 홈 칸과 같은 열쇠: 서기 STAND, 트랙 아님). 홈 칸이 처음 그려질 때 붙잡아 둔 파일을 바로 받아 주 스레드에서 파일을
 * 읽지 않는다. 합성 텍스처는 [prepareRiveAvatarLook]이 공용 캐시에 따로 넣는다.
 *
 * 주 스레드 코루틴에서 부르고 취소될 때까지 붙잡는다. 취소되면 놓고, 놓은 뒤 잠시 안에 홈 칸이 가져가지 않은 파일은 버린다. 준비 실패는
 * 던지지 않는다(홈 칸이 다시 읽는다).
 */
suspend fun holdHomeRiveAvatar(context: Context, gender: AvatarGender) {
    val hold = try {
        val prepared = RiveAvatarAssets.shared.load(context, gender)
        RiveRuntime.ensureInitialized(context)
        RivFiles.shared.hold(RivKey(prepared, AvatarMotion.STAND, forTrack = false))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.w(e, "home avatar warmup skipped")
        null
    } catch (e: LinkageError) {
        Timber.w(e, "home avatar warmup skipped")
        null
    }
    try {
        awaitCancellation()
    } finally {
        hold?.release()
    }
}
