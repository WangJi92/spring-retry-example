package com.wangji92.retry.springretryexample.service;

import com.wangji92.retry.springretryexample.dto.TextMessageDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.remoting.RemoteAccessException;
import org.springframework.retry.annotation.*;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author 汪小哥
 * @date 03-04-2021
 */
@Component
@Slf4j
public class StatefulRetryTestService {

    private static final int DELAY_TIME = 5000;

    @Autowired
    private RestTemplate restTemplate;

    private AtomicInteger invokeCount = new AtomicInteger(1);

    /**
     * https://www.zhihu.com/question/265289234/answer/952517156
     * 何为有状态？
     * 有状态重试通常是用在message-driven 的应用中，从消息中间件比如RabbitMQ等接收到的消息，如果应用处理失败，
     * 那么消息中间件服务器会再次投递，再次投递时，对于集成了Spring Retry的应用来说，
     * 再次处理之前处理失败的消息，就是一次重试；也就是说，
     * Spring Retry能够识别出，当前正在处理的消息是否是之前处理失败过的消息；
     * <p>
     * 【能够识别之前处理调用失败的此时，根据 参数的 key唯一标识 TextMessageDto类的hashcode and equals 影响非常大】
     *
     * @param messageDto
     * @return
     */
    @Retryable(value = RemoteAccessException.class,
            backoff = @Backoff(DELAY_TIME),
            maxAttempts = 2,
            recover = "recover",
            stateful = true,// 这个很重要!!!!
            label = "stateFullRetryTestSendMessage"
    )
    public Integer stateFullRetryTestSendMessage(TextMessageDto messageDto) {
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
    public Integer recover(RemoteAccessException e, TextMessageDto messageDto) {
        if (messageDto != null) {
            log.error("stateFullRetryTestSendMessage error  messageId={}", messageDto.getMessageId());
        }
        String stack = Arrays.toString(Thread.currentThread().getStackTrace());
        stack = stack.replaceAll(",", "\n");
        log.info("recover is begin : 堆栈 \n {}", stack);
        ResponseEntity<String> responseEntity = restTemplate.getForEntity("http://localhost:8080/unstableApi/200", String.class);
        log.info("remote response is {}", responseEntity.getBody());
        return Integer.parseInt(Objects.requireNonNull(responseEntity.getBody()));
    }


    /**
     * recover 的返回值 要和 retry的一致 【int Integer 不一样哦】
     * recover 的第一个参数是异常 其他的和调用方法的参数保持一致...
     * {@link AnnotationAwareRetryOperationsInterceptor#getDelegate(java.lang.Object, java.lang.reflect.Method)}
     *
     * @param args
     */
    public static void main(String[] args) {
        StatefulRetryTestService statefulRetryTestService = new StatefulRetryTestService();
        RecoverAnnotationRecoveryHandler<?> handler = new RecoverAnnotationRecoveryHandler<>(
                statefulRetryTestService, ReflectionUtils.findMethod(StatefulRetryTestService.class, "stateFullRetryTestSendMessage", TextMessageDto.class));

        TextMessageDto messageDto = new TextMessageDto();
        messageDto.setMessageId("001");

        handler.recover(new Object[]{messageDto}, new RuntimeException("Planned"));
    }


}
