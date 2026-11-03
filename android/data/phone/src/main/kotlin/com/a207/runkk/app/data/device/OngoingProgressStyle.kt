package com.a207.runkk.app.data.device

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.a207.runkk.app.data.phone.R
import com.a207.runkk.app.domain.run.ProgressNotice

/** Android 15 이하(또는 승격을 끈 기기)의 일반 진행 알림. 같은 제목·본문과 setProgress 막대다. */
class OngoingProgressStyle(private val context: Context) : ProgressNotificationStyle {
    override fun build(notice: ProgressNotice): Notification {
        val text = ProgressNoticeText.of(context, notice)
        val builder = progressNotificationBase(context, text)
        text.progressMax?.let { builder.setProgress(it, text.progress ?: 0, false) }
        return builder.build()
    }
}
