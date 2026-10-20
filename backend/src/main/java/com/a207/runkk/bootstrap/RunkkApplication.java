package com.a207.runkk.bootstrap;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * 애플리케이션 시작점이자 컴포넌트 탐색 진입점(설계 5.1.1 bootstrap). 시작 클래스가 bootstrap 하위에 있으므로 탐색 범위를 서버 기본 패키지 전체로
 * 넓힌다. 패키지는 문자열로만 지정해 bootstrap → common 외의 import를 만들지 않는다(12.2 D66).
 */
@SpringBootApplication(scanBasePackages = RunkkApplication.BASE_PACKAGE)
@EntityScan(basePackages = RunkkApplication.BASE_PACKAGE)
@EnableJpaRepositories(basePackages = RunkkApplication.BASE_PACKAGE)
public class RunkkApplication {

    static final String BASE_PACKAGE = "com.a207.runkk";

    /** 서비스가 생성자로 받는 시계(설계 D-10). 서비스 시각은 한국 시간 기준이다. */
    @Bean
    Clock clock() {
        return Clock.system(ZoneId.of("Asia/Seoul"));
    }

    public static void main(String[] args) {
        SpringApplication.run(RunkkApplication.class, args);
    }
}
