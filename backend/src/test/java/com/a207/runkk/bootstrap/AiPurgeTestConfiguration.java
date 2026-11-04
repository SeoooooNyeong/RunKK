package com.a207.runkk.bootstrap;

import com.a207.runkk.aiprivacy.AiDataEraser;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

/** 시험 전용 클래스패스에서만 탐색된다. 환경변수와 무관하게 실제 AI DELETE를 차단한다. */
@Configuration
@Profile({"test", "integration"})
public class AiPurgeTestConfiguration {
    @Bean
    @Primary
    AiDataEraser testAiDataEraser() {
        return memberId -> {};
    }
}
