package com.a207.runkk.app.core.navigation

/*
 * 크루 초대 링크의 약속([임시:D-성-237], [임시:D-성-238], [임시:D-성-314]). 링크를 만드는 쪽(ui.crew 초대 창)과 읽는 쪽
 * (app InviteLinkReader)이 같은 값을 쓰도록 한곳에 둔다. 친구 대결 초대(/invite/battle/{token}, 실행 인자 invite)와 경로·인자 이름이
 * 달라 서로 다른 화면으로 간다.
 */

/** 초대 링크 호스트(매니페스트 앱 링크 intent-filter와 같다). */
const val INVITE_LINK_HOST = "k15a207.p.ssafy.io"

/** 크루 초대 앱 링크 경로 머리말: `https://k15a207.p.ssafy.io/invite/crew/{CODE}`. */
const val CREW_INVITE_PATH = "/invite/crew/"

/** 카카오톡 실행 링크의 크루 초대 인자 이름: `kakao{앱 키}://kakaolink?crewInvite={CODE}`(`invite`는 친구 대결 전용). */
const val CREW_INVITE_PARAM = "crewInvite"

/** 크루 초대 코드의 글자 수(서버 InviteCode: 대문자와 숫자 6자). */
const val CREW_INVITE_CODE_LENGTH = 6

/** 그 크루의 지금 초대 코드를 가리키는 초대 링크. */
fun crewInviteUrl(code: String): String = "https://$INVITE_LINK_HOST$CREW_INVITE_PATH$code"
