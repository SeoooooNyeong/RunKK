package com.a207.runkk.web.admin;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 인터셉터 적용 경로를 설정한다(설계 6.1.13 web.admin.AdminWebConfig, D-50). /api/v1/admin/** 에
 * OperatorCheckInterceptor를 걸고 운영자 로그인(/api/v1/admin/login)만 뺀다.
 */
@Configuration
public class AdminWebConfig implements WebMvcConfigurer {

    static final String ADMIN_PATHS = "/api/v1/admin/**";
    static final String LOGIN_PATH = "/api/v1/admin/login";

    private final OperatorCheckInterceptor operatorCheckInterceptor;

    public AdminWebConfig(OperatorCheckInterceptor operatorCheckInterceptor) {
        this.operatorCheckInterceptor = operatorCheckInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(operatorCheckInterceptor)
                .addPathPatterns(ADMIN_PATHS)
                .excludePathPatterns(LOGIN_PATH);
    }
}
