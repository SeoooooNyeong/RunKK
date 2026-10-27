package com.a207.runkk.realtime;

import com.a207.runkk.member.AccessGrant;
import com.a207.runkk.member.AuthFailureException;
import com.a207.runkk.member.TokenIssuer;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * /ws 핸드셰이크 단계에서 접근 토큰을 확인한다(설계 5.7.2, 6.1.13, design-review.md E1). 토큰이 없거나 틀리면(만료 포함) 업그레이드하지 않고
 * HTTP 401로 거부해 앱이 토큰을 갱신하고 다시 연결하게 한다(B3 리뷰: 연결 뒤 1008 종료 대신). 맞으면 TokenIssuer.verifyGrant의 회원 ID와
 * 토큰 만료 시각을 세션 속성에 붙이고, 연결 뒤 SocketSessionRegistry가 그 시각에 4401로 닫는다(D-110).
 */
@Component
@ConditionalOnProperty(name = "runkk.live", havingValue = "websocket", matchIfMissing = true)
class TokenHandshakeInterceptor implements HandshakeInterceptor {

    private static final Logger log = LoggerFactory.getLogger(TokenHandshakeInterceptor.class);
    private static final String BEARER = "Bearer ";

    private final TokenIssuer tokenIssuer;

    TokenHandshakeInterceptor(TokenIssuer tokenIssuer) {
        this.tokenIssuer = tokenIssuer;
    }

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {
        Optional<AccessGrant> grant =
                grantOf(tokenIssuer, request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
        if (grant.isEmpty()) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        attributes.put(LiveSocketHandler.MEMBER_ID, grant.get().memberId());
        attributes.put(LiveSocketHandler.EXPIRES_AT, grant.get().expiresAt());
        return true;
    }

    /** Bearer 헤더의 접근 토큰을 검증한다. 없거나 틀리면(위조·만료·다른 종류) 비어 있다. 토큰 값은 로그에 남기지 않는다. */
    static Optional<AccessGrant> grantOf(TokenIssuer tokenIssuer, String header) {
        if (header == null || !header.startsWith(BEARER)) {
            return Optional.empty();
        }
        try {
            return Optional.of(tokenIssuer.verifyGrant(header.substring(BEARER.length())));
        } catch (AuthFailureException e) {
            log.info("socket_auth_failed");
            return Optional.empty();
        }
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception) {
        // 할 일 없음
    }
}
