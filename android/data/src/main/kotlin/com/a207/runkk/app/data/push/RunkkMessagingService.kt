package com.a207.runkk.app.data.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.a207.runkk.app.api.core.R
import com.a207.runkk.app.domain.notification.PushTokenSync
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * FCM 등록 토큰 변경과 앱이 떠 있을 때 온 푸시를 받는다(6.1.14 data.push, D-43, D-102, D-130).
 * - onNewToken → PushTokenSync.onTokenChanged. 토큰 값은 로그에 쓰지 않는다.
 * - 앱이 뒤에 있으면 FCM 알림 메시지는 OS가 띄운다. 앱이 앞에 있으면 OS가 띄우지 않으므로 여기서 띄우되, 표시 전에
 *   OpenChatRoomTracker에 묻는다: 열린 채팅방 크루의 CREW_CHAT은 띄우지 않는다(FR-218). 같은 묶음 키의 알림은 하나로 묶는다.
 * google-services.json이 없는 빌드에서는 Firebase가 초기화되지 않아 이 서비스가 불리지 않는다.
 */
@AndroidEntryPoint
class RunkkMessagingService : FirebaseMessagingService() {
    @Inject
    lateinit var pushTokenSync: PushTokenSync

    @Inject
    lateinit var openChatRooms: OpenChatRoomTracker

    override fun onNewToken(token: String) {
        pushTokenSync.onTokenChanged(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val notification = message.notification ?: return
        if (!openChatRooms.shouldDisplay(message.data[TYPE], message.collapseKey)) return
        if (!canPost()) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.push_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
        val launch = packageManager.getLaunchIntentForPackage(packageName)
        val content =
            launch?.let {
                PendingIntent.getActivity(
                    this,
                    0,
                    it,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            }
        val shown =
            NotificationCompat
                .Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setContentTitle(notification.title)
                .setContentText(notification.body)
                .setContentIntent(content)
                .setAutoCancel(true)
                .build()
        @Suppress("MissingPermission") // canPost가 알림 권한을 확인했다.
        NotificationManagerCompat.from(this).notify(message.collapseKey ?: message.messageId, NOTIFICATION_ID, shown)
    }

    /** Android 13부터 알림 권한(POST_NOTIFICATIONS)이 있어야 띄운다. */
    private fun canPost(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

    private companion object {
        const val TYPE = "type"
        const val CHANNEL_ID = "runkk-push"
        const val NOTIFICATION_ID = 1
    }
}
