package com.a207.runkk.app.ui.share

import android.content.Context
import android.content.Intent
import android.net.Uri
import javax.inject.Inject

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
        return shareFailureOrNull("공유 시트 열기 실패") {
            context.startActivity(chooser)
            ShareOutcome.Shared
        } ?: ShareOutcome.Failed("NO_SHARE_APP", R.string.share_no_share_app)
    }
}
