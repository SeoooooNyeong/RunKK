package com.a207.runkk.app.ui.share

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import javax.inject.Inject

/** 기기 저장: 사진첩(Pictures/RunKK)에 PNG로 넣는다. */
class DeviceSaveTarget @Inject constructor() : ShareTarget {
    override fun share(context: Context, image: Uri): ShareOutcome {
        val resolver = context.contentResolver
        val pending = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "runkk_${System.currentTimeMillis()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, PNG)
            if (pending) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/RunKK")
                // 다 쓰기 전에는 사진첩에 보이지 않게 한다.
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val target =
            shareFailureOrNull("사진첩 항목 만들기 실패") {
                resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            }
                ?: return ShareOutcome.Failed(SAVE_FAILED, R.string.share_save_failed_check)
        val saved = shareFailureOrNull("사진첩에 쓰기 실패") {
            resolver.openInputStream(image).use { input ->
                resolver.openOutputStream(target).use { output -> requireNotNull(input).copyTo(requireNotNull(output)) }
            }
            if (pending) {
                resolver.update(
                    target,
                    ContentValues().apply {
                        put(MediaStore.Images.Media.IS_PENDING, 0)
                    },
                    null,
                    null
                )
            }
            ShareOutcome.Saved
        }
        if (saved != null) return saved
        // 반쯤 쓴 빈 항목을 남기지 않는다.
        shareFailureOrNull("반쯤 쓴 사진첩 항목 지우기 실패") { resolver.delete(target, null, null) }
        return ShareOutcome.Failed(SAVE_FAILED, R.string.share_save_failed)
    }

    private companion object {
        const val SAVE_FAILED = "SAVE_FAILED"
    }
}
