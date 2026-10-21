package com.a207.runkk.app.ui.share

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.StringRes
import javax.inject.Inject

/** 카드 앞면·뒷면(앞면 기록, 뒷면 지도 + 경로선, FR-068). */
enum class CardSide { FRONT, BACK }

/** 공유 결과. 화면이 문구로 바꿔 보인다. */
sealed interface ShareOutcome {
    /** 다른 앱(공유 시트·스토리)으로 넘겼다. */
    data object Shared : ShareOutcome

    /** 기기 사진첩에 저장했다. */
    data object Saved : ShareOutcome

    /** 원래 대상 대신 다른 방법을 썼거나 안내가 필요하다(인스타그램 없음). */
    data class Fallback(@StringRes val messageRes: Int) : ShareOutcome

    /** 공유하지 못했다. [code]는 사유 코드(COURSE_MAP_NOT_SHARED 등). */
    data class Failed(val code: String, @StringRes val messageRes: Int) : ShareOutcome
}

/** 카드 이미지 하나를 한 곳에 공유한다(6.1.14, C-012). [image]는 FileProvider content 주소. */
interface ShareTarget {
    fun share(context: Context, image: Uri): ShareOutcome
}

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
        val target = runCatching { resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) }.getOrNull()
            ?: return ShareOutcome.Failed(SAVE_FAILED, R.string.share_save_failed_check)
        return runCatching {
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
        }.getOrElse {
            // 반쯤 쓴 빈 항목을 남기지 않는다.
            runCatching { resolver.delete(target, null, null) }
            ShareOutcome.Failed(SAVE_FAILED, R.string.share_save_failed)
        }
    }

    private companion object {
        const val SAVE_FAILED = "SAVE_FAILED"
    }
}

/** 다른 앱: Android 공유 시트(ACTION_SEND). */
class SystemShareTarget @Inject constructor() : ShareTarget {
    override fun share(context: Context, image: Uri): ShareOutcome {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = PNG
            putExtra(Intent.EXTRA_STREAM, image)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, context.getString(R.string.share_chooser_title)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(chooser)
            ShareOutcome.Shared
        }.getOrElse { ShareOutcome.Failed("NO_SHARE_APP", R.string.share_no_share_app) }
    }
}

/**
 * 스토리: 인스타그램 스토리 인텐트. 앱이 없으면 [deviceSave]로 기기에 저장하고 안내한다(6.1.14).
 * Meta 앱 ID(source_application)는 통합 단계에서 설정 값으로 넣는다.
 */
class StoryShareTarget @Inject constructor(private val deviceSave: DeviceSaveTarget) : ShareTarget {
    override fun share(context: Context, image: Uri): ShareOutcome {
        val story = Intent(INSTAGRAM_STORY).apply {
            setDataAndType(image, PNG)
            setPackage(INSTAGRAM)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val installed = context.packageManager.queryIntentActivities(story, 0).isNotEmpty()
        if (installed) {
            context.grantUriPermission(INSTAGRAM, image, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val started = runCatching { context.startActivity(story) }.isSuccess
            if (started) return ShareOutcome.Shared
        }
        return when (deviceSave.share(context, image)) {
            ShareOutcome.Saved -> ShareOutcome.Fallback(R.string.share_story_saved_fallback)
            else -> ShareOutcome.Fallback(R.string.share_story_no_instagram)
        }
    }

    private companion object {
        const val INSTAGRAM = "com.instagram.android"
        const val INSTAGRAM_STORY = "com.instagram.share.ADD_TO_STORY"
    }
}

private const val PNG = "image/png"
