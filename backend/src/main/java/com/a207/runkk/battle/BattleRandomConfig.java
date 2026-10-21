package com.a207.runkk.battle;

import java.security.SecureRandom;
import java.util.random.RandomGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 고스트 후보·가상 기록 선택에 쓰는 무작위 생성기(설계 5.1.1 "설정 클래스는 설정하는 패키지에"). 시험은 씨앗을 고정한 생성기를 넘긴다. */
@Configuration(proxyBeanMethods = false)
class BattleRandomConfig {

    @Bean
    RandomGenerator battleRandom() {
        return new SecureRandom();
    }
}
