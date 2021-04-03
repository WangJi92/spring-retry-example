package com.wangji92.retry.springretryexample.task;

import com.wangji92.retry.springretryexample.dto.TextMessageDto;
import com.wangji92.retry.springretryexample.service.StatefulRetryTestService;
import com.wangji92.retry.springretryexample.utils.AopTargetUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Configuration;
import org.springframework.remoting.RemoteAccessException;
import org.springframework.retry.RecoveryCallback;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryState;
import org.springframework.retry.annotation.AnnotationAwareRetryOperationsInterceptor;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.interceptor.StatefulRetryOperationsInterceptor;
import org.springframework.retry.policy.MapRetryContextCache;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.DefaultRetryState;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 有状态的测试
 *
 * @author 汪小哥
 * @date 03-04-2021
 */
@Component
@Slf4j
public class SchedulingTestStateFullRetryTask {

    @Autowired
    private StatefulRetryTestService statefulRetryTestService;

    private static final int DELAY_TIME = 5000;

    /**
     * aop的方式测试
     */
    @ConditionalOnExpression("#{'true'.equals(environment['aopStateFullRetry'])}")
    @Configuration
    public class AopStateFullRetry {
        @Scheduled(fixedRate = 30000)
        @ConditionalOnExpression("#{'false'.equals(environment['programmingRetry'])}")
        public void retryTestService() {
            TextMessageDto messageDto = new TextMessageDto();
            messageDto.setMessageId("001");
            int responseBody = statefulRetryTestService.stateFullRetryTestSendMessage(messageDto);
            log.info("AopStateFullRetry response result is {}", responseBody);

        }
    }

    /**
     * 编程方式测试
     * {@link AnnotationAwareRetryOperationsInterceptor#getDelegate(java.lang.Object, java.lang.reflect.Method)}
     * {@link StatefulRetryOperationsInterceptor#invoke(org.aopalliance.intercept.MethodInvocation)}
     */
    @ConditionalOnExpression("#{'true'.equals(environment['programmingStateFullRetry'])}")
    @Configuration
    public class ProgrammingStateFullRetry {

        @Scheduled(fixedRate = 30000)
        public void retryTestService() throws Throwable {

            RetryTemplate template = new RetryTemplate();

            SimpleRetryPolicy simple = new SimpleRetryPolicy();
            simple.setMaxAttempts(2);
            template.setRetryPolicy(simple);

            FixedBackOffPolicy backOffPolicy = new FixedBackOffPolicy();
            backOffPolicy.setBackOffPeriod(DELAY_TIME);
            template.setBackOffPolicy(backOffPolicy);


            TextMessageDto messageDto = new TextMessageDto();
            messageDto.setMessageId("001");

            // 调用的状态标识 默认为 label+方法参数
            /**
             * {@link StatefulRetryOperationsInterceptor#createKey(org.aopalliance.intercept.MethodInvocation, java.lang.Object)}
             */
            RetryState state = new DefaultRetryState(messageDto, false, null);

            // 调用失败的缓存 都存放在内存里面的! SoftReferenceMapRetryContextCache
            template.setRetryContextCache(new MapRetryContextCache());

            // 获取原始的对象
            StatefulRetryTestService targetStatefulRetryTestService = (StatefulRetryTestService) AopTargetUtils.getTarget(statefulRetryTestService);


            Integer responseBody = template.execute(new RetryCallback<Integer, Throwable>() {

                @Override
                public Integer doWithRetry(RetryContext context) throws Throwable {
                    return targetStatefulRetryTestService.stateFullRetryTestSendMessage(messageDto);
                }
            }, new RecoveryCallback<Integer>() {
                @Override
                public Integer recover(RetryContext context) throws Exception {
                    return statefulRetryTestService.recover((RemoteAccessException) context.getLastThrowable(), messageDto);
                }
            }, state);

            log.info("programmingStateFullRetry response result is {}", responseBody);

        }
    }
}
