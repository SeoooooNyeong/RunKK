package com.a207.runkk;

import com.a207.runkk.bootstrap.RunkkApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes = RunkkApplication.class)
@ActiveProfiles("test")
class RunkkApplicationTests {

    @Test
    void contextLoads() {}
}
