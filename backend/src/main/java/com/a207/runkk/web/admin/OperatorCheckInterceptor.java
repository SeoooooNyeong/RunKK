package com.a207.runkk.web.admin;

import com.a207.runkk.common.PermissionDeniedException;
import com.a207.runkk.operator.OperatorAuthorizer;
import com.a207.runkk.operator.OperatorId;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 관리툴 요청을 보낸 운영자를 확인한다(설계 6.1.13 web.admin.OperatorCheckInterceptor, D-42, D-50). Authorization:
 * Bearer 관리툴 토큰을 OperatorAuthorizer.ensureOperator로 확인하고 운영자 계정 문자열을 요청 속성 {@value #ACTOR_ID}로 붙인다.
 * 토큰 없음·위조·만료· 화이트리스트 밖이면 PermissionDeniedException(web.error가 403으로 바꾼다). 업무 규칙을 갖지 않는다.
 */
@Component
public class OperatorCheckInterceptor implements HandlerInterceptor {

    /**
     * web.admin.celebrity·setting·audit 컨트롤러와 web.admin의 RankingAdminController와의 계약(요청 속성 이름,
     * D-50).
     */
    public static final String ACTOR_ID = "actorId";

    private static final String BEARER = "Bearer ";

    private final OperatorAuthorizer operatorAuthorizer;

    public OperatorCheckInterceptor(OperatorAuthorizer operatorAuthorizer) {
        this.operatorAuthorizer = operatorAuthorizer;
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request, HttpServletResponse response, Object handler) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER)) {
            throw new PermissionDeniedException("관리툴 토큰 없음");
        }
        OperatorId operator = operatorAuthorizer.ensureOperator(header.substring(BEARER.length()));
        request.setAttribute(ACTOR_ID, operator.actorId());
        return true;
    }
}
