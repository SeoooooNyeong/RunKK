package com.a207.runkk.app

import android.content.Intent
import android.net.Uri
import com.a207.runkk.app.core.navigation.CREW_INVITE_CODE_LENGTH
import com.a207.runkk.app.core.navigation.CREW_INVITE_PARAM
import com.a207.runkk.app.core.navigation.CREW_INVITE_PATH
import com.a207.runkk.app.core.navigation.INVITE_LINK_HOST
import java.util.Locale

/**
 * 들어온 링크에서 초대를 꺼낸다. 친구 대결 초대 토큰(D-272, FR-229)은 [read]·[readNew]가, 크루 초대까지 가른 값([InviteLink],
 * [임시:D-성-242])은 [readLink]·[readNewLink]가 준다. 친구 대결 초대로 받는 링크는 둘이다.
 * - 앱 링크 `https://k15a207.p.ssafy.io/invite/battle/{token}`(서버가 만든 inviteUrl, 웹 공유·브라우저에서 열림)
 * - 카카오톡 실행 링크 `kakao{앱 키}://kakaolink?invite={token}`(카카오톡 초대 메시지의 "대결 참가하기")
 * 토큰 모양(영숫자·하이픈·밑줄 1~64자, 서버 토큰은 base64url 22자)이 아니면 버린다. 다른 호스트·경로·스킴도 버린다.
 * 토큰은 서버 조회(GET …/by-token/{token})의 경로 조각으로만 쓰고, 화면에 그대로 보이지 않는다.
 *
 * 크루 초대로 받는 링크도 둘이다([임시:D-성-237], [임시:D-성-238]). 경로와 실행 인자 이름이 대결과 달라 서로 섞이지 않는다.
 * - 앱 링크 `https://k15a207.p.ssafy.io/invite/crew/{CODE}`(초대 창의 링크 복사·다른 앱 공유)
 * - 카카오톡 실행 링크 `kakao{앱 키}://kakaolink?crewInvite={CODE}`(카카오톡 초대 메시지의 "크루 가입하기")
 * 코드는 대문자로 바꿔 읽고 대문자·숫자 6자가 아니면 버린다.
 */
class InviteLinkReader(private val kakaoScheme: String) {
    /** VIEW 인텐트의 링크에서 토큰. 초대 링크가 아니면 null. */
    fun read(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_VIEW) return null
        return intent.data?.let(::read)
    }

    /**
     * 새로 받은 링크에서만 토큰을 꺼낸다(AN8-7). 최근 앱 목록에서 다시 연 실행(FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)은 처음 실행 인텐트를
     * 그대로 다시 주므로 이미 처리한 초대로 보고 읽지 않는다.
     */
    fun readNew(intent: Intent?): String? {
        if (intent == null || intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return null
        return read(intent)
    }

    /** VIEW 인텐트의 링크가 가리키는 초대(친구 대결 또는 크루). 초대 링크가 아니면 null. */
    fun readLink(intent: Intent?): InviteLink? {
        if (intent?.action != Intent.ACTION_VIEW) return null
        return intent.data?.let(::readLink)
    }

    /** 새로 받은 링크에서만 초대를 꺼낸다([readNew]와 같은 규칙, AN8-7). */
    fun readNewLink(intent: Intent?): InviteLink? {
        if (intent == null || intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return null
        return readLink(intent)
    }

    fun readLink(uri: Uri): InviteLink? =
        read(uri)?.let(InviteLink::FriendBattle) ?: readCrewCode(uri)?.let(InviteLink::Crew)

    /** 크루 초대 링크의 코드(대문자로 바꾼 6자). 크루 초대 링크가 아니거나 코드 모양이 아니면 null. */
    private fun readCrewCode(uri: Uri): String? {
        val scheme = uri.scheme?.lowercase() ?: return null
        val code = when {
            scheme == HTTPS && uri.host.equals(HOST, ignoreCase = true) ->
                uri.path?.takeIf { it.startsWith(CREW_INVITE_PATH) }?.removePrefix(CREW_INVITE_PATH)

            kakaoScheme.isNotBlank() && scheme == kakaoScheme.lowercase() && uri.host == KAKAO_LINK_HOST ->
                uri.getQueryParameter(CREW_INVITE_PARAM)

            else -> null
        }
        // 모양을 먼저 보고 대문자로 바꾼다(대문자로 바꾸면 글자 수가 달라지는 문자를 받지 않게).
        return code?.takeIf { CREW_CODE.matches(it) }?.uppercase(Locale.ROOT)
    }

    fun read(uri: Uri): String? {
        val scheme = uri.scheme?.lowercase() ?: return null
        val token = when {
            scheme == HTTPS && uri.host.equals(HOST, ignoreCase = true) ->
                uri.path?.takeIf { it.startsWith(PATH_PREFIX) }?.removePrefix(PATH_PREFIX)

            kakaoScheme.isNotBlank() && scheme == kakaoScheme.lowercase() && uri.host == KAKAO_LINK_HOST ->
                uri.getQueryParameter(INVITE_PARAM)

            else -> null
        }
        return token?.takeIf { TOKEN.matches(it) }
    }

    companion object {
        private const val HTTPS = "https"

        /** 초대 링크 호스트(서버 runkk.battle.friend-invite.link-base). 매니페스트 앱 링크 intent-filter와 같다. */
        const val HOST = INVITE_LINK_HOST
        const val PATH_PREFIX = "/invite/battle/"

        /** 카카오톡 실행 링크 호스트와 쿼리 이름(ui.battle KakaoInviteSharer.INVITE_PARAM과 같다). */
        const val KAKAO_LINK_HOST = "kakaolink"
        const val INVITE_PARAM = "invite"

        private val TOKEN = Regex("[A-Za-z0-9_-]{1,64}")

        /** 크루 초대 코드 모양(서버 InviteCode: 대문자·숫자 6자). 소문자로 온 것은 대문자로 바꿔 읽는다. */
        private val CREW_CODE = Regex("[A-Za-z0-9]{$CREW_INVITE_CODE_LENGTH}")

        /** 처리한 링크를 비운 인텐트(AN8-7). Activity.setIntent로 바꿔 두어 다시 읽어도 같은 초대가 다시 열리지 않게 한다. */
        fun consumed(intent: Intent): Intent = Intent(intent).setData(null)
    }
}
