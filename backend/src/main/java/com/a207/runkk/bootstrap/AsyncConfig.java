package com.a207.runkk.bootstrap;

import java.lang.reflect.Method;
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 비동기 사건 수신자(D-03: 리포트, Runimal)의 실행기. 인스턴스 1개·동시 사용자 50명 규모(5.4)에 맞춘 작은 풀이다. 실패는 운영 로그로만 남기고 인자
 * 값(회원 자료)은 남기지 않는다.
 */
@Configuration(proxyBeanMethods = false)
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    @Override
    public TaskExecutor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("runkk-async-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
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
