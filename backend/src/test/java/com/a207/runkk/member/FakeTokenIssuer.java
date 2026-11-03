package com.a207.runkk.member;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 시험용 TokenIssuer. 토큰 문자열에 회원 ID·계정을 그대로 담는다. 워치용 갱신 토큰은 "refresh-{회원}-{순번}-w{세대}"이고 회원의 워치 세션 세대가
 * 바뀌면 거부한다(W25).
 */
public class FakeTokenIssuer implements TokenIssuer {

    private final Map<String, SocialAccount> tickets = new HashMap<>();
    private final Set<String> revoked = new HashSet<>();
    private final Map<Long, Long> watchGenerations = new HashMap<>();
    private int issued;
    private boolean watchStoreDown;

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

    /** 만료 시각은 지금 + 1시간(접근 토큰 수명). */
    @Override
    public AccessGrant verifyGrant(String accessToken) {
        return new AccessGrant(verify(accessToken), java.time.Instant.now().plusSeconds(3_600));
    }

    @Override
    public void revoke(String refreshToken) {
        revoked.add(refreshToken);
    }

    @Override
    public RefreshGrant redeemRefreshToken(String refreshToken) {
        if (refreshToken == null
                || !refreshToken.startsWith("refresh-")
                || isWatchSessionRevoked(refreshToken)
                || !revoked.add(refreshToken)) {
            throw new AuthFailureException("갱신 토큰 오류");
        }
        String[] parts = refreshToken.split("-");
        long memberId = Long.parseLong(parts[1]);
        return isWatch(refreshToken)
                ? new RefreshGrant.Watch(memberId, generationOf(refreshToken))
                : new RefreshGrant.Phone(memberId);
    }

    @Override
    public TokenPair issueWatch(long memberId) {
        checkWatchStore();
        return watchPair(memberId, watchGenerations.getOrDefault(memberId, 0L));
    }

    @Override
    public TokenPair reissue(RefreshGrant grant) {
        return switch (grant) {
            case RefreshGrant.Watch w -> watchPair(w.memberId(), w.generation());
            case RefreshGrant.Phone p -> issue(p.memberId());
        };
    }

    @Override
    public void revokeWatchSessions(long memberId) {
        checkWatchStore();
        watchGenerations.merge(memberId, 1L, Long::sum);
    }

    @Override
    public void revokeExistingWatchSessions(long memberId) {
        checkWatchStore();
        watchGenerations.computeIfPresent(memberId, (id, g) -> g + 1);
    }

    @Override
    public void forgetWatchSessions(long memberId) {
        checkWatchStore();
        watchGenerations.remove(memberId);
    }

    /** 그 회원의 워치 세션 세대 행이 있는가. */
    public boolean hasWatchGenerationRow(long memberId) {
        return watchGenerations.containsKey(memberId);
    }

    private TokenPair watchPair(long memberId, long generation) {
        issued++;
        return new TokenPair(
                "access-" + memberId, "refresh-" + memberId + "-" + issued + "-w" + generation);
    }

    private void checkWatchStore() {
        if (watchStoreDown) {
            throw new ProviderUnavailableException("워치 세션 저장소 장애");
        }
    }

    private static long generationOf(String refreshToken) {
        return Long.parseLong(refreshToken.substring(refreshToken.lastIndexOf("-w") + 2));
    }

    /** 워치용(POST /auth/watch-session으로 발급했거나 그것을 회전한) 갱신 토큰인가. */
    public boolean isWatch(String refreshToken) {
        return refreshToken.contains("-w");
    }

    /** 워치용 갱신 토큰인데 발급 뒤 그 회원의 워치 세션이 폐기되었는가. */
    public boolean isWatchSessionRevoked(String refreshToken) {
        if (!isWatch(refreshToken)) {
            return false;
        }
        long memberId = Long.parseLong(refreshToken.split("-")[1]);
        return generationOf(refreshToken) != watchGenerations.getOrDefault(memberId, 0L);
    }

    /** 워치 세션 저장소 장애를 흉내 낸다(issueWatch·revokeWatchSessions가 ProviderUnavailableException). */
    public void watchStoreDown() {
        watchStoreDown = true;
    }

    @Override
    public int purgeExpiredRevocations(java.time.LocalDateTime now) {
        return 0;
    }

    public int issuedCount() {
        return issued;
    }

    public boolean isRevoked(String refreshToken) {
        return revoked.contains(refreshToken);
    }
}
