package com.a207.runkk.app.data.device

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.a207.runkk.app.data.phone.R
import com.a207.runkk.app.domain.run.ProgressNotice

/**
 * Android 16 이상 승격 진행 알림(Live Updates). ProgressStyle 막대(끝 = 목표 거리·대결 거리), 승격 요청, 상태 표시줄 칩 글자.
 * 승격 조건(진행 중, 제목 있음, 사용자 지정 뷰 없음, 색 지정 없음)을 지킨다.
 */
class LiveUpdateStyle(private val context: Context) : ProgressNotificationStyle {
    override fun build(notice: ProgressNotice): Notification {
        val text = ProgressNoticeText.of(context, notice)
        val style = NotificationCompat.ProgressStyle()
        text.progressMax?.let { max ->
            style.addProgressSegment(NotificationCompat.ProgressStyle.Segment(max))
            style.setProgress(text.progress ?: 0)
        }
        return progressNotificationBase(context, text)
            .setStyle(style)
            .setRequestPromotedOngoing(true)
            .setShortCriticalText(text.chip)
            .build()
    }
}
