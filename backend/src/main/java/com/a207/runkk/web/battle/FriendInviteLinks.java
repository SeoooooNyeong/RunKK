package com.a207.runkk.web.battle;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 친구 대결 초대 링크 주소(설계 5.6.2 web.battle.FriendInviteLinks, D-219). 링크 = linkBase + "/" + 토큰. linkBase가
 * 비면 기동이 실패한다(조용히 틀린 링크를 만들지 않는다).
 *
 * @param linkBase runkk.battle.friend-invite.link-base(기본 https://k15a207.p.ssafy.io/invite/battle)
 */
@ConfigurationProperties("runkk.battle.friend-invite")
public record FriendInviteLinks(String linkBase) {

    public FriendInviteLinks {
        if (linkBase == null || linkBase.isBlank()) {
            throw new IllegalArgumentException("runkk.battle.friend-invite.link-base가 비었다");
        }
        linkBase = linkBase.strip();
        while (linkBase.endsWith("/")) {
            linkBase = linkBase.substring(0, linkBase.length() - 1);
        }
    }

    /** 토큰의 초대 링크(base64url 토큰이라 인코딩할 문자가 없다). */
    public String urlOf(String token) {
        return linkBase + "/" + token;
    }
}
