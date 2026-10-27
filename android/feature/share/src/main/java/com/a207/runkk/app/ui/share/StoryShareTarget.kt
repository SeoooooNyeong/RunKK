package com.a207.runkk.app.ui.share

import android.content.Context
import android.content.Intent
import android.net.Uri
import javax.inject.Inject

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
            val started = shareFailureOrNull("인스타그램 스토리 열기 실패") { context.startActivity(story) } != null
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
