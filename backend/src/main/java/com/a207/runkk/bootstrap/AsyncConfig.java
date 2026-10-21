package com.a207.runkk.bootstrap;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 비동기 사건 수신자(D-03: 리포트, Runimal)의 실행기. 실행기는 Spring Boot가 spring.task.execution.* 설정으로 만드는 컨테이너 빈
 * (applicationTaskExecutor)을 그대로 써서 종료 때 진행 중 작업을 기다린다(1단계 리뷰 M6·L2, 풀 크기·대기열·종료 대기는
 * application.yml). 실패는 운영 로그로만 남기고 인자 값(회원 자료)은 남기지 않는다.
 */
@Configuration(proxyBeanMethods = false)
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    private final Executor applicationTaskExecutor;

    public AsyncConfig(@Qualifier("applicationTaskExecutor") Executor applicationTaskExecutor) {
        this.applicationTaskExecutor = applicationTaskExecutor;
    }

    @Override
    public Executor getAsyncExecutor() {
        return applicationTaskExecutor;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (Throwable ex, Method method, Object... params) ->
                log.error(
                        "async_listener_failed method={} paramTypes={}",
                        method.getDeclaringClass().getSimpleName() + "." + method.getName(),
                        Arrays.stream(params)
                                .map(p -> p == null ? "null" : p.getClass().getSimpleName())
                                .toList(),
                        ex);
    }
}
