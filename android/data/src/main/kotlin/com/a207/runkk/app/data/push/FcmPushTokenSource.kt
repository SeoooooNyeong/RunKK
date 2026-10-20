package com.a207.runkk.app.data.push

import android.content.Context
import com.a207.runkk.app.domain.notification.PushTokenSource
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.tasks.await

/**
 * 지금 FCM 등록 토큰을 읽는다(6.1.14 data.push, PushTokenSource 구현). FirebaseMessaging.token을 읽는다.
 * Firebase가 초기화되지 않았거나(google-services.json 없는 빌드, Play 서비스 없음) 토큰을 받지 못하면 null이다.
 * 그러면 PushTokenSync가 등록을 건너뛰고 다음 앱 시작·로그인 때 다시 읽는다.
 */
class FcmPushTokenSource
@Inject
constructor(@ApplicationContext private val context: Context) : PushTokenSource {
    override suspend fun currentToken(): String? {
        if (FirebaseApp.getApps(context).isEmpty()) return null
        return try {
            FirebaseMessaging.getInstance().token.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }
}
