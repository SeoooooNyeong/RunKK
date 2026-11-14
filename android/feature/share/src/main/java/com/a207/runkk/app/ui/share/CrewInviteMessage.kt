package com.a207.runkk.app.ui.share

/**
 * 카카오톡으로 보낼 크루 초대 메시지의 내용([임시:FR-성-37], [임시:D-성-240]). [code]는 그 크루의 지금 초대 코드(실행 인자
 * crewInvite로 실린다), [expiresOn]은 화면에 보일 유효 기간 날짜("11월 21일", 모르면 빈 문자열), [webUrl]은 초대 링크
 * (`https://k15a207.p.ssafy.io/invite/crew/{CODE}`)다.
 */
data class CrewInviteMessage(val crewName: String, val code: String, val expiresOn: String, val webUrl: String)
