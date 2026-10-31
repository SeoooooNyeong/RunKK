package com.a207.runkk.app.data.device

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.a207.runkk.app.api.core.R
import com.a207.runkk.app.domain.run.ProgressNotice

/**
 * 진행 알림 한 장을 그린다(D-259, FR-224). OS 판에 따라 구현 하나를 고른다: Android 16(API 36) 이상이고 승격 알림을 올릴 수
 * 있으면 [LiveUpdateStyle](Live Updates, ProgressStyle), 아니면 [OngoingProgressStyle](같은 내용의 일반 진행 알림)이다.
 * 문구는 [ProgressNoticeText]가 한 번 만들고, 두 구현은 같은 제목·본문·동작 버튼을 쓴다.
 */
interface ProgressNotificationStyle {
    fun build(notice: ProgressNotice): Notification

    companion object {
        /** Live Updates(승격 진행 알림)가 들어온 API 수준(Android 16). */
        const val LIVE_UPDATE_API = 36

        /**
         * 알림 표현을 고르는 조건(TC-9755~TC-9757): SDK가 [LIVE_UPDATE_API] 이상이고 승격 알림을 올릴 수 있을 때만 참이다.
         * SDK가 낮으면 [canPostPromoted]를 부르지 않는다(그 아래 판에는 그 API가 없다).
         */
        fun isLiveUpdate(sdkInt: Int, canPostPromoted: () -> Boolean): Boolean =
            sdkInt >= LIVE_UPDATE_API && canPostPromoted()

        fun choose(context: Context, sdkInt: Int, canPostPromoted: () -> Boolean): ProgressNotificationStyle =
            if (isLiveUpdate(sdkInt, canPostPromoted)) LiveUpdateStyle(context) else OngoingProgressStyle(context)
    }
}

/**
 * 두 표현이 함께 쓰는 알림 바탕: 채널·아이콘·제목·본문·앱 열기·동작 버튼. 소리·진동은 내지 않는다(LOW 채널, onlyAlertOnce).
 * 동작 버튼은 MeasurementService를 action과 함께 부른다(D-260).
 */
internal fun progressNotificationBase(context: Context, text: ProgressNoticeText): NotificationCompat.Builder {
    val open = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
        PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE)
    }
    val builder = NotificationCompat
        .Builder(context, MeasurementService.CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_menu_mylocation)
        .setContentTitle(text.title)
        .setContentText(text.body)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        .setContentIntent(open)
    text.action?.let { action ->
        val (label, intentAction) = when (action) {
            ProgressNoticeText.Action.PAUSE -> R.string.progress_notice_pause to MeasurementService.ACTION_PAUSE
            ProgressNoticeText.Action.RESUME -> R.string.progress_notice_resume to MeasurementService.ACTION_RESUME
        }
        val intent = Intent(context, MeasurementService::class.java).setAction(intentAction)
        val pending = PendingIntent.getService(
            context,
            action.ordinal,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        builder.addAction(0, context.getString(label), pending)
    }
    return builder
}
