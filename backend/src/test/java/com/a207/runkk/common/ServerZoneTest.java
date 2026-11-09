package com.a207.runkk.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.TimeZone;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("서버 시간대(DF-073)")
class ServerZoneTest {

    @Test
    @DisplayName("useAsJvmDefault는 JVM 기본 시간대를 Asia/Seoul로 맞춘다")
    void useAsJvmDefault_setsSeoul() {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

            ServerZone.useAsJvmDefault();

            assertThat(TimeZone.getDefault().getID()).isEqualTo("Asia/Seoul");
            assertThat(TimeZone.getDefault().toZoneId()).isEqualTo(ServerZone.ID);
        } finally {
            TimeZone.setDefault(original);
        }
    }
}
