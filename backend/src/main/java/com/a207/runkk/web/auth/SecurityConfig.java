package com.a207.runkk.web.auth;

import com.a207.runkk.member.TokenIssuer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 인증 필터의 적용 경로를 설정한다(설계 6.1.13 web.auth.SecurityConfig). 로그인·토큰 갱신·가입과 법정 고지 문서(가입 화면이 읽는다)만 공개하고,
 * /api/v1/admin/*은 회원 인증 필터에서 뺀다(D-42, 관리툴 토큰은 web.admin이 확인). 나머지는 AuthFilter가 붙인 회원만 들어온다. Basic
 * 인증·폼 로그인·세션은 쓰지 않는다(1단계 리뷰 L11). H2 콘솔은 local 프로필에서만 연다(L12). Spring Security 6.x 람다 DSL만 쓴다.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String API = "/api/v1";

    /**
     * 실시간 WebSocket 핸드셰이크(5.7.2). 접근 토큰은 realtime.LiveSocketHandler가 TokenIssuer.verify로 확인한다(E1).
     */
    private static final String LIVE_SOCKET = "/ws";

    /** local 프로필에서만 H2 콘솔(프레임 사용)을 연다. */
    @Bean
    @Order(1)
    @Profile("local")
    SecurityFilterChain h2ConsoleChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/h2-console/**")
                .csrf(AbstractHttpConfigurer::disable)
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain apiChain(HttpSecurity http, TokenIssuer tokenIssuer) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(
                        errors ->
                                errors.authenticationEntryPoint(
                                                (request, response, e) ->
                                                        AuthFilter.writeUnauthorized(response))
                                        .accessDeniedHandler(
                                                (request, response, e) ->
                                                        response.sendError(
                                                                HttpStatus.FORBIDDEN.value())))
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers(
                                                "/actuator/health",
                                                "/actuator/health/**",
                                                "/actuator/info",
                                                "/docs",
                                                "/docs/**",
                                                "/swagger-ui/**",
                                                "/v3/api-docs/**",
                                                "/error",
                                                LIVE_SOCKET)
                                        .permitAll()
                                        .requestMatchers(
                                                HttpMethod.POST,
                                                API + "/auth/login",
                                                API + "/auth/refresh",
                                                API + "/members")
                                        .permitAll()
                                        .requestMatchers(HttpMethod.GET, API + "/legal/**")
                                        .permitAll()
                                        .requestMatchers(API + "/admin/**")
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())
                .addFilterBefore(
                        new AuthFilter(tokenIssuer), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
