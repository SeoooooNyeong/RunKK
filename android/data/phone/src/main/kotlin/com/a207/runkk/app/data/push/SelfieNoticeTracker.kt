package com.a207.runkk.app.data.push

import android.Manifest
import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.a207.runkk.app.data.phone.R
import com.a207.runkk.app.domain.avatar.SelfieResultNotifier
import com.a207.runkk.app.domain.avatar.SelfieScreenPresence
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 셀카 얼굴 결과 기기 알림(design-oo 05-30 [임시:D-성-120], [임시:DR-성-2], [임시:FR-성-21]). domain.avatar의 두 포트를 구현한다
 * (OpenChatRoomTracker 선례, D-130): 얼굴 만들기 화면이 열고 닫는 [SelfieScreenPresence]와 워커가 부르는 [SelfieResultNotifier].
 *
 * - 알림은 푸시와 같은 채널(`runkk-push`)에 작업마다 하나다. 누르면 앱을 열고 실행 인텐트에 작업 ID
 *   ([SelfieResultNotifier.EXTRA_JOB_ID])를 싣는다.
 * - 내지 않는 때: 얼굴 만들기 화면이 보일 때, 앱이 앞에 있을 때(화면이 진행 표시로 바로 바뀐다), 알림 권한이 없을 때(Android 13부터).
 * - 같은 작업 ID로 두 번 불러도 한 번만 낸다(프로세스 안).
 */
@Singleton
class SelfieNoticeTracker
internal constructor(
    private val context: Context,
    private val isAppInForeground: () -> Boolean,
    private val canPost: () -> Boolean
) : SelfieScreenPresence,
    SelfieResultNotifier {
    @Inject
    constructor(@ApplicationContext context: Context) : this(
        context,
        isAppInForeground = ::isProcessInForeground,
        canPost = { hasNotificationPermission(context) }
    )

    private val openScreens = AtomicInteger(0)
    private val notified: MutableSet<String> = ConcurrentHashMap.newKeySet()

    override val isVisible: Boolean get() = openScreens.get() > 0

    override fun opened() {
        openScreens.incrementAndGet()
    }

    override fun closed() {
        openScreens.updateAndGet { (it - 1).coerceAtLeast(0) }
    }

    override fun notify(jobId: String, succeeded: Boolean) {
        if (!notified.add(jobId)) return
        if (isVisible || isAppInForeground() || !canPost()) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.push_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
        val launch =
            context.packageManager
                .getLaunchIntentForPackage(context.packageName)
                ?.putExtra(SelfieResultNotifier.EXTRA_JOB_ID, jobId)
        val content =
            launch?.let {
                PendingIntent.getActivity(
                    context,
                    jobId.hashCode(),
                    it,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            }
        val shown =
            NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setContentTitle(
                    context.getString(
                        if (succeeded) R.string.selfie_notice_done_title else R.string.selfie_notice_failed_title
                    )
                ).setContentText(
                    context.getString(
                        if (succeeded) R.string.selfie_notice_done_body else R.string.selfie_notice_failed_body
                    )
                ).setContentIntent(content)
                .setAutoCancel(true)
                .build()
        @Suppress("MissingPermission") // canPost가 알림 권한을 확인했다.
        NotificationManagerCompat.from(context).notify(TAG, jobId.hashCode(), shown)
    }

    internal companion object {
        const val CHANNEL_ID = "runkk-push"
        const val TAG = "selfie"
    }
}

/** 이 프로세스가 앞에 보이는가(화면 하나라도 보임). */
private fun isProcessInForeground(): Boolean {
    val info = ActivityManager.RunningAppProcessInfo()
    ActivityManager.getMyMemoryState(info)
    return info.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
}

/** Android 13부터 알림 권한(POST_NOTIFICATIONS)이 있어야 낸다. */
private fun hasNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
