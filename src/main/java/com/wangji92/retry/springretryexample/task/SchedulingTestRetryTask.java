package com.wangji92.retry.springretryexample.task;

import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategies;
import com.wangji92.retry.springretryexample.service.RetryTestService;
import com.wangji92.retry.springretryexample.utils.AopTargetUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Configuration;
import org.springframework.remoting.RemoteAccessException;
import org.springframework.retry.RecoveryCallback;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.retry.annotation.AnnotationAwareRetryOperationsInterceptor;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * @author 汪小哥
 * @date 02-04-2021
 */
@Component
@Slf4j
public class SchedulingTestRetryTask {


    private static final int DELAY_TIME = 5000;

    @Autowired
    private RetryTestService retryTestService;

    @Autowired
    private RetryListener retryListener;

    /**
     * aop的方式测试
     */
    @ConditionalOnExpression("#{'true'.equals(environment['aopSpringRetry'])}")
    @Configuration
    public class AopSpringRetry {

        @Scheduled(fixedRate = 30000)
        public void retryTestService() {
            int responseBody = retryTestService.retryTestService();
            log.info("retryTestService response result is {}", responseBody);

        }
    }

    /**
     * 编程的方式 测试 retry
     * {@link AnnotationAwareRetryOperationsInterceptor#getDelegate(java.lang.Object, java.lang.reflect.Method)}
     * {@link RetryOperationsInterceptor#invoke(org.aopalliance.intercept.MethodInvocation)}
     */
    @ConditionalOnExpression("#{'true'.equals(environment['programmingSpringRetry'])}")
    @Configuration
    public class ProgrammingSpringRetry {
        @Scheduled(fixedRate = 30000)
        public void programmingSpringRetry() throws Exception {
            // 获取原始的对象
            RetryTestService targetRetryTestService = (RetryTestService) AopTargetUtils.getTarget(retryTestService);

            RetryTemplate retryTemplate = RetryTemplate.builder()
                    .maxAttempts(2)
                    .fixedBackoff(DELAY_TIME)
                    .retryOn(RemoteAccessException.class)
                    .traversingCauses()
                    // 非必须
                    .withListener(retryListener)
                    .build();

            Integer responseBody = retryTemplate.execute(new RetryCallback<Integer, RemoteAccessException>() {

                @Override
                public Integer doWithRetry(RetryContext context) throws RemoteAccessException {
                    return targetRetryTestService.retryTestService();
                }
            }, new RecoveryCallback<Integer>() {
                // 垫底方案
                @Override
                public Integer recover(RetryContext context) throws Exception {
                    return targetRetryTestService.recover((RemoteAccessException) context.getLastThrowable());
                }
            });
            log.info("programmingRetry retryTestService response result is {}", responseBody);

        }
    }

    /**
     * guava
     */
    @ConditionalOnExpression("#{'true'.equals(environment['programmingGuavaRetry'])}")
    @Configuration
    public class ProgrammingGuavaRetry {
        @Scheduled(fixedRate = 30000)
        public void programmingGuavaRetry() throws Exception {
            // 获取原始的对象
            RetryTestService targetRetryTestService = (RetryTestService) AopTargetUtils.getTarget(retryTestService);

            // RetryerBuilder 构建重试实例 guavaRetryer,可以设置重试源且可以支持多个重试源，可以配置重试次数或重试超时时间，以及可以配置等待时间间隔
            Retryer<Integer> guavaRetryer = RetryerBuilder.<Integer>newBuilder()
                    //设置异常重试源 根据异常 也可以 retryIfResult 根据结果
                    .retryIfExceptionOfType(RemoteAccessException.class)

                    //设置等待间隔时间
                    .withWaitStrategy(WaitStrategies.fixedWait(5, TimeUnit.SECONDS))
                    //设置最大重试次数
                    .withStopStrategy(StopStrategies.stopAfterAttempt(2))
                    .build();
            Integer responseBody = null;
            try {
                responseBody = guavaRetryer.call(targetRetryTestService::retryTestService);
                log.info("guava retry retryTestService response result is {}", responseBody);
            } catch (Exception e) {
                log.info("guava retry error", e);
                responseBody = targetRetryTestService.recover(null);
            }

            log.info("guava retry retryTestService response result is {}", responseBody);


        }
    }


}
