package com.a207.runkk.app.ui.share

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.res.Resources
import android.net.Uri
import com.kakao.sdk.common.util.KakaoCustomTabsClient
import com.kakao.sdk.share.ShareClient
import com.kakao.sdk.share.WebSharerClient
import com.kakao.sdk.template.model.Button
import com.kakao.sdk.template.model.Content
import com.kakao.sdk.template.model.FeedTemplate
import com.kakao.sdk.template.model.Link
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * 카카오톡 링크 공유의 공통 코드([임시:D-성-240]). 지금은 크루 초대 메시지를 보낸다([임시:FR-성-37]): 카카오톡이 있으면 기본 템플릿
 * (FeedTemplate)으로 카카오톡을 열고, 없으면 카카오톡 웹 공유 주소를 브라우저로 연다. 단추 링크에는 실행 인자(crewInvite = 초대 코드)와
 * 웹 링크(초대 링크)를 함께 단다: 앱이 있으면 실행 링크로 앱이 열려 가입 확인 화면으로 가고([임시:FR-성-38]), 없으면 카카오톡이
 * 스토어로 보낸다. KakaoSdk.init은 app이 시작 때 한 번 한다(D-275). 앱 키가 없어 초기화되지 않았으면 SDK 호출이 실패하고
 * [KakaoShareOutcome.FAILED]다. 친구 대결 초대(ui.battle KakaoInviteSharer)와는 따로 둔다(ui.crew는 ui.battle을 모른다).
 */
class KakaoLinkSharer @Inject constructor() {
    suspend fun shareCrewInvite(activity: Activity, invite: CrewInviteMessage): KakaoShareOutcome {
        val template = crewInviteTemplate(activity.resources, invite)
        return try {
            if (ShareClient.instance.isKakaoTalkSharingAvailable(activity)) {
                shareToTalk(activity, template)
            } else {
                shareToWeb(activity, template)
            }
        } catch (e: IllegalStateException) {
            // 카카오 SDK가 초기화되지 않았다(앱 키 없음).
            KakaoShareOutcome.FAILED
        } catch (e: UninitializedPropertyAccessException) {
            // 초기화 전 SDK는 KakaoSdk.hosts(lateinit)를 읽다 이 예외를 던진다(IllegalStateException이 아니다).
            KakaoShareOutcome.FAILED
        }
    }

    private suspend fun shareToTalk(activity: Activity, template: FeedTemplate): KakaoShareOutcome =
        suspendCancellableCoroutine { continuation ->
            ShareClient.instance.shareDefault(activity, template) { result, _ ->
                val shared = if (result != null) {
                    try {
                        activity.startActivity(result.intent)
                        KakaoShareOutcome.KAKAO_TALK
                    } catch (e: ActivityNotFoundException) {
                        KakaoShareOutcome.FAILED
                    }
                } else {
                    KakaoShareOutcome.FAILED
                }
                if (continuation.isActive) continuation.resume(shared)
            }
        }

    /** 카카오톡이 없으면 웹 공유 주소를 기본 브라우저(없으면 맞춤 탭을 지원하는 다른 브라우저)로 연다. */
    private fun shareToWeb(activity: Activity, template: FeedTemplate): KakaoShareOutcome {
        val url = WebSharerClient.instance.makeDefaultUrl(template)
        return try {
            KakaoCustomTabsClient.openWithDefault(activity, url)
            KakaoShareOutcome.WEB
        } catch (e: UnsupportedOperationException) {
            openAnyBrowser(activity, url)
        } catch (e: ActivityNotFoundException) {
            openAnyBrowser(activity, url)
        }
    }

    private fun openAnyBrowser(activity: Activity, url: Uri): KakaoShareOutcome = try {
        KakaoCustomTabsClient.open(activity, url)
        KakaoShareOutcome.WEB
    } catch (e: ActivityNotFoundException) {
        KakaoShareOutcome.FAILED
    }

    companion object {
        /**
         * 실행 링크의 크루 초대 인자 이름(kakao{앱 키}://kakaolink?crewInvite=코드). 읽는 쪽(app InviteLinkReader)은
         * core.navigation의 CREW_INVITE_PARAM을 쓰고, 두 값이 같은지는 ui.crew 시험이 본다(ui.share는 core.navigation을 모른다).
         */
        const val CREW_INVITE_PARAM = "crewInvite"

        /** 초대 카드 그림(서버 nginx 정적 파일). 앱 링크 경로(/invite/crew/, /invite/battle/) 밖이라 그림을 눌러도 앱 링크로 잡히지 않는다. */
        const val INVITE_CARD_IMAGE = "https://k15a207.p.ssafy.io/invite/card.png"
    }
}

/**
 * 크루 초대 메시지([임시:FR-성-37]): 제목은 크루 이름, 설명은 초대 문장과 유효 기간(날짜를 모르면 초대 문장만), 단추는 「크루 가입하기」다.
 * 단추 링크만 실행 인자를 갖는다(카드 본문을 누르면 초대 링크가 열린다).
 */
internal fun crewInviteTemplate(resources: Resources, invite: CrewInviteMessage): FeedTemplate {
    val web = Link(webUrl = invite.webUrl, mobileWebUrl = invite.webUrl)
    val app = Link(
        webUrl = invite.webUrl,
        mobileWebUrl = invite.webUrl,
        androidExecutionParams = mapOf(KakaoLinkSharer.CREW_INVITE_PARAM to invite.code)
    )
    val description = if (invite.expiresOn.isBlank()) {
        resources.getString(R.string.share_crew_invite_description)
    } else {
        resources.getString(R.string.share_crew_invite_description_until, invite.expiresOn)
    }
    return FeedTemplate(
        content = Content(
            title = invite.crewName,
            imageUrl = KakaoLinkSharer.INVITE_CARD_IMAGE,
            link = web,
            description = description
        ),
        buttons = listOf(Button(resources.getString(R.string.share_crew_invite_button), app))
    )
}
