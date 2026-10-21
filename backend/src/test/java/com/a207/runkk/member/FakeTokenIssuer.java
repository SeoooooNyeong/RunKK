package com.a207.runkk.member;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** 시험용 TokenIssuer. 토큰 문자열에 회원 ID·계정을 그대로 담는다. */
public class FakeTokenIssuer implements TokenIssuer {

    private final Map<String, SocialAccount> tickets = new HashMap<>();
    private final Set<String> revoked = new HashSet<>();
    private int issued;

    @Override
    public TokenPair issue(long memberId) {
        issued++;
        return new TokenPair("access-" + memberId, "refresh-" + memberId + "-" + issued);
    }

    @Override
    public String issueSignUpTicket(SocialAccount account) {
        String ticket = "ticket-" + account.provider() + "-" + account.providerAccountId();
        tickets.put(ticket, account);
        return ticket;
    }

    @Override
    public SocialAccount redeemSignUpTicket(String ticket) {
        SocialAccount account = tickets.get(ticket);
        if (account == null) {
            throw new AuthFailureException("가입 티켓 없음");
        }
        return account;
    }

    @Override
    public long verify(String accessToken) {
        if (accessToken == null || !accessToken.startsWith("access-")) {
            throw new AuthFailureException("위조");
        }
        return Long.parseLong(accessToken.substring("access-".length()));
    }

    @Override
    public void revoke(String refreshToken) {
        revoked.add(refreshToken);
    }

    @Override
    public long redeemRefreshToken(String refreshToken) {
        if (refreshToken == null
                || !refreshToken.startsWith("refresh-")
                || !revoked.add(refreshToken)) {
            throw new AuthFailureException("갱신 토큰 오류");
        }
        return Long.parseLong(refreshToken.split("-")[1]);
    }

    public boolean isRevoked(String refreshToken) {
        return revoked.contains(refreshToken);
    }
}
