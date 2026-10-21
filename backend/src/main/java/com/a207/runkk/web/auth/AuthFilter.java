package com.a207.runkk.web.auth;

import com.a207.runkk.member.AuthFailureException;
import com.a207.runkk.member.TokenIssuer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * JWT에서 회원 ID를 꺼내 요청에 붙인다(설계 6.1.13 web.auth.AuthFilter). Authorization: Bearer 접근 토큰을
 * member.TokenIssuer.verify로 확인하고 회원 ID를 요청 속성 memberId와 보안 문맥에 둔다. 만료·위조면 바로 401이다.
 * /api/v1/admin/*은 회원 JWT를 검사하지 않는다(관리툴 토큰은 web.admin의 OperatorCheckInterceptor가 본다, D-42, D-50).
 * 토큰 값은 로그에 남기지 않는다.
 */
public class AuthFilter extends OncePerRequestFilter {

    /** 컨트롤러가 {@code @RequestAttribute("memberId")}로 받는 요청 속성 이름. */
    public static final String MEMBER_ID = "memberId";

    static final String ADMIN_PATH = "/api/v1/admin/";
    private static final String BEARER = "Bearer ";
    private static final String UNAUTHORIZED_BODY =
            "{\"code\":\"UNAUTHORIZED\",\"message\":\"로그인이 필요합니다\"}";

    private final TokenIssuer tokenIssuer;

    public AuthFilter(TokenIssuer tokenIssuer) {
        this.tokenIssuer = tokenIssuer;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith(request.getContextPath() + ADMIN_PATH);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER)) {
            chain.doFilter(request, response);
            return;
        }
        long memberId;
        try {
            memberId = tokenIssuer.verify(header.substring(BEARER.length()));
        } catch (AuthFailureException e) {
            SecurityContextHolder.clearContext();
            writeUnauthorized(response);
            return;
        }
        request.setAttribute(MEMBER_ID, memberId);
        SecurityContextHolder.getContext()
                .setAuthentication(
                        UsernamePasswordAuthenticationToken.authenticated(
                                memberId, null, List.of()));
        chain.doFilter(request, response);
    }

    static void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(UNAUTHORIZED_BODY);
    }
}
