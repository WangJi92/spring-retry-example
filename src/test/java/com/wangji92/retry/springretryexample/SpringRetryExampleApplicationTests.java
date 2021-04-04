package com.wangji92.retry.springretryexample;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.TimeUnit;

@SpringBootTest
@Slf4j
class SpringRetryExampleApplicationTests {

    @Test
    void contextLoads() {
        try {
            // 1、foreach do remote invoke
            // 2、record error invoke
            // 3、sleep some time
            TimeUnit.MINUTES.sleep(5);
            // 4、foreach do remote invoke error record
            // 5、record error again and ding talk robot alarm
        } catch (InterruptedException e) {
        }
    }

}
