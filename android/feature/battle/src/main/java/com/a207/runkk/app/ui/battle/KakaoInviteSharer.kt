package com.a207.runkk.app.ui.battle

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.res.Resources
import android.net.Uri
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.domain.battle.FriendInvite
import com.kakao.sdk.common.util.KakaoCustomTabsClient
import com.kakao.sdk.share.ShareClient
import com.kakao.sdk.share.WebSharerClient
import com.kakao.sdk.template.model.Button
import com.kakao.sdk.template.model.Content
import com.kakao.sdk.template.model.FeedTemplate
import com.kakao.sdk.template.model.Link
import java.util.Locale
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * 카카오톡 공유(D-270). 카카오톡이 있으면 기본 템플릿(FeedTemplate)으로 카카오톡을 열고, 없으면 카카오톡 웹 공유 주소를 브라우저로 연다.
 * 버튼 링크에는 실행 인자(invite = 토큰)와 웹 링크(서버가 만든 inviteUrl)를 함께 단다: 앱이 있으면 실행 링크로 앱이 열리고(D-272),
 * 없으면 카카오톡이 Play 스토어로 보낸다(FR-229). KakaoSdk.init은 app이 시작 때 한 번 한다(D-275). 앱 키가 없어 초기화되지 않았으면
 * SDK 호출이 실패하고 [ShareResult.FAILED]다.
 */
class KakaoInviteSharer @Inject constructor() : InviteSharer {
    override suspend fun share(activity: Activity, invite: FriendInvite, inviterName: String?): ShareResult {
        val template = templateOf(invite, inviterName, activity)
        return try {
            if (ShareClient.instance.isKakaoTalkSharingAvailable(
                    activity
                )
            ) {
                shareToTalk(activity, template)
            } else {
                shareToWeb(activity, template)
            }
        } catch (e: IllegalStateException) {
            // 카카오 SDK가 초기화되지 않았다(앱 키 없음).
            ShareResult.FAILED
        } catch (e: UninitializedPropertyAccessException) {
            // 초기화 전 SDK는 KakaoSdk.hosts(lateinit)를 읽다 이 예외를 던진다(IllegalStateException이 아니다).
            ShareResult.FAILED
        }
    }

    private suspend fun shareToTalk(activity: Activity, template: FeedTemplate): ShareResult =
        suspendCancellableCoroutine { continuation ->
            ShareClient.instance.shareDefault(activity, template) { result, _ ->
                val shared = if (result != null) {
                    try {
                        activity.startActivity(result.intent)
                        ShareResult.KAKAO_TALK
                    } catch (e: ActivityNotFoundException) {
                        ShareResult.FAILED
                    }
                } else {
                    ShareResult.FAILED
                }
                if (continuation.isActive) continuation.resume(shared)
            }
        }

    /** 카카오톡이 없으면 웹 공유 주소를 기본 브라우저(없으면 맞춤 탭을 지원하는 다른 브라우저)로 연다. */
    private fun shareToWeb(activity: Activity, template: FeedTemplate): ShareResult {
        val url = WebSharerClient.instance.makeDefaultUrl(template)
        return try {
            KakaoCustomTabsClient.openWithDefault(activity, url)
            ShareResult.WEB
        } catch (e: UnsupportedOperationException) {
            openAnyBrowser(activity, url)
        } catch (e: ActivityNotFoundException) {
            openAnyBrowser(activity, url)
        }
    }

    private fun openAnyBrowser(activity: Activity, url: Uri): ShareResult = try {
        KakaoCustomTabsClient.open(activity, url)
        ShareResult.WEB
    } catch (e: ActivityNotFoundException) {
        ShareResult.FAILED
    }

    private fun templateOf(invite: FriendInvite, inviterName: String?, activity: Activity): FeedTemplate {
        val web = Link(webUrl = invite.inviteUrl, mobileWebUrl = invite.inviteUrl)
        val app = Link(
            webUrl = invite.inviteUrl,
            mobileWebUrl = invite.inviteUrl,
            androidExecutionParams = mapOf(INVITE_PARAM to invite.token)
        )
        return FeedTemplate(
            content = Content(
                title = inviteShareTitle(activity.resources, inviterName, invite.distance),
                imageUrl = INVITE_CARD_IMAGE,
                link = web,
                description = activity.getString(R.string.battle_invite_share_description)
            ),
            buttons = listOf(Button(activity.getString(R.string.battle_invite_share_button), app))
        )
    }

    companion object {
        /** 실행 링크 쿼리 이름(kakao{앱 키}://kakaolink?invite=토큰). app InviteLinkReader가 같은 이름을 읽는다. */
        const val INVITE_PARAM = "invite"

        /**
         * 초대 카드 그림(서버 D-293 nginx 정적 파일). 앱 링크 경로(/invite/battle/) 밖에 두어 그림을 눌러도 앱 링크로 잡히지 않는다(AN8-9).
         */
        const val INVITE_CARD_IMAGE = "https://k15a207.p.ssafy.io/invite/card.jpg"
    }
}

private const val METERS_PER_KM = 1_000.0

/**
 * 초대 메시지 제목(D-270, TC-9673): "바람처럼님이 5 km 대결을 신청했어요"(SRS 띄어쓰기). 내 닉네임을 못 읽었으면 이름 없는 제목(AN8-2).
 */
internal fun inviteShareTitle(resources: Resources, inviterName: String?, distance: Distance): String {
    val km = distance.meters / METERS_PER_KM
    val label = if (km % 1.0 == 0.0) km.toLong().toString() else String.format(Locale.US, "%.1f", km)
    return if (inviterName.isNullOrBlank()) {
        resources.getString(R.string.battle_invite_share_title_anonymous, label)
    } else {
        resources.getString(R.string.battle_invite_share_title, inviterName, label)
    }
}
