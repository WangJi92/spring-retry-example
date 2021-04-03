package com.wangji92.retry.springretryexample.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.remoting.RemoteAccessException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author 汪小哥
 * @date 02-04-2021
 */
@Component
@Slf4j
public class RetryTestService {

    private static final int DELAY_TIME = 5000;

    @Autowired
    private RestTemplate restTemplate;

    private AtomicInteger invokeCount = new AtomicInteger(1);


    @Retryable(value = RemoteAccessException.class,
            // 退避策略 休息 5秒继续
            backoff = @Backoff(DELAY_TIME),

            // 重试策略 最大一个两次 包含第一次
            maxAttempts = 2,
            // 兜底方案 全部失败 调用当前类中的兜底方法
            recover = "recover"
    )
    public Integer retryTestService() {
        int count = invokeCount.getAndIncrement();
        String url = "http://localhost:8080/unstableApi/500";
        if (count % 2 == 0 && count % 5 == 0) {
            url = "http://localhost:8080/unstableApi/200";
        }
        try {
            ResponseEntity<String> responseEntity = restTemplate.getForEntity(url, String.class);
        } catch (Exception e) {
            log.info("try get unstable api failed", e);
            throw new RemoteAccessException("500", e);
        }
        return 500;
    }

    /**
     * 作为恢复处理程序的方法调用的注释。合适的恢复处理程序具有Throwable类型（或Throwable的子类型）的第一个参数和与要从中恢复的@Retryable方法相同类型的返回值。Throwable第一个参数是可选的（但是没有它的方法只有在没有其他参数匹配时才会被调用）。后续参数按顺序从失败方法的参数列表中填充
     *
     * @param e
     */
    @Recover
    public Integer recover(RemoteAccessException e) {
        String stack = Arrays.toString(Thread.currentThread().getStackTrace());
        stack = stack.replaceAll(",", "\n");
        log.info("recover is begin : 堆栈 \n {}", stack);
        ResponseEntity<String> responseEntity = restTemplate.getForEntity("http://localhost:8080/unstableApi/200", String.class);
        log.info("remote response is {}", responseEntity.getBody());
        return Integer.parseInt(Objects.requireNonNull(responseEntity.getBody()));
    }

}
