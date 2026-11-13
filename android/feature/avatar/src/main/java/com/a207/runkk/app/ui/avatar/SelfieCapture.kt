package com.a207.runkk.app.ui.avatar

import android.content.Context
import java.io.File

/**
 * 카메라 셀카 원본의 앱 전용 임시 파일(캐시 `selfie_capture/capture.jpg`, [SelfieFileProvider], 실기기 DV3-3). 원본에는 EXIF·위치 정보가
 * 있으므로 읽은 뒤·읽지 못했을 때·화면을 떠나 취소됐을 때 모두 지우고, 앞 촬영에서 남은 것은 다음 촬영·앨범 고르기 직전에 지운다
 * (FR-156, 리뷰 RC-2). 화면에 들어올 때 지우지 않는 것은 프로세스가 다시 만들어진 뒤 늦게 오는 카메라 결과의 파일을 지우지 않기 위해서다.
 */
internal object SelfieCapture {
    fun fileOf(context: Context): File = File(File(context.cacheDir, DIR), FILE)

    /** 촬영 직전: 남은 원본을 지우고 폴더를 만든다(리뷰 RC-4). 카메라가 쓸 파일을 돌려준다. */
    fun prepare(context: Context): File = fileOf(context).apply {
        delete()
        parentFile?.mkdirs()
    }

    /** 남은 원본을 지운다(앨범을 고를 때, 카메라를 취소했을 때). */
    fun clear(context: Context) {
        fileOf(context).delete()
    }

    /** [read]로 원본을 읽고 성공·실패·예외(취소 포함) 어느 쪽이든 [file]을 지운다. */
    fun <T> readThenDelete(file: File, read: () -> T): T = try {
        read()
    } finally {
        file.delete()
    }

    private const val DIR = "selfie_capture"
    private const val FILE = "capture.jpg"
}
