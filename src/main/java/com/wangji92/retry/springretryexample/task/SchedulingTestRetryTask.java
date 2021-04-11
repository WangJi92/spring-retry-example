package com.wangji92.retry.springretryexample.task;

import com.github.rholder.retry.*;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.SimpleTimeLimiter;
import com.google.common.util.concurrent.TimeLimiter;
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

import javax.annotation.Nonnull;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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

    /**
     * guava withAttemptTimeLimiter 这里如果多线程支持 会导致很多的问题 上下文传递、而且框架没有人升级了
     */
    @ConditionalOnExpression("#{'true'.equals(environment['programmingGuavaRetryLimitTime'])}")
    @Configuration
    public class ProgrammingGuavaRetryLimitTime {
        @Scheduled(fixedRate = 30000)
        public void programmingGuavaRetry() throws Exception {
            // 获取原始的对象
            RetryTestService targetRetryTestService = (RetryTestService) AopTargetUtils.getTarget(retryTestService);

            // RetryerBuilder 构建重试实例 guavaRetryer,可以设置重试源且可以支持多个重试源，可以配置重试次数或重试超时时间，以及可以配置等待时间间隔
            Retryer<Integer> guavaRetryer = RetryerBuilder.<Integer>newBuilder()
                    //设置异常重试源 根据异常 也可以 retryIfResult 根据结果
                    .retryIfExceptionOfType(RemoteAccessException.class)

                    // 【这里将会使用多线程执行(other 线程执行、会导致事务问题、线程上下文传递问题 一定要小心)】 还有这个框架 这个属性高版本不支持了.
                    .withAttemptTimeLimiter(new FixedAttemptTimeLimit<Integer>(1, TimeUnit.MINUTES))
                    //设置等待间隔时间
                    .withWaitStrategy(WaitStrategies.fixedWait(5, TimeUnit.SECONDS))
                    //设置最大重试次数
                    .withStopStrategy(StopStrategies.stopAfterAttempt(2))
                    .build();
            Integer responseBody = null;
            try {
                log.info("guava retry retryTestService begin thread name={}", Thread.currentThread().getName());
                responseBody = guavaRetryer.call(() -> {
                    log.info("current thread name={}", Thread.currentThread().getName());
                    return targetRetryTestService.retryTestService();
                });
                log.info("guava retry retryTestService response result is {}", responseBody);
            } catch (Exception e) {
                log.info("guava retry error", e);
                responseBody = targetRetryTestService.recover(null);
            }

            log.info("guava retry retryTestService response result is {}", responseBody);


        }


        /**
         * {@literal https://github.com/rholder/guava-retrying/issues/66 这个框架没有人支持升级}
         * guava retry 这里过期了..
         *
         * @param <V>
         */
        private final class FixedAttemptTimeLimit<V> implements AttemptTimeLimiter<V> {

            private final TimeLimiter timeLimiter;
            private final long duration;
            private final TimeUnit timeUnit;

            public FixedAttemptTimeLimit(long duration, @Nonnull TimeUnit timeUnit) {
                this(SimpleTimeLimiter.create(Executors.newFixedThreadPool(10)), duration, timeUnit);
            }

            public FixedAttemptTimeLimit(long duration, @Nonnull TimeUnit timeUnit, @Nonnull ExecutorService executorService) {
                this(SimpleTimeLimiter.create(executorService), duration, timeUnit);
            }

            private FixedAttemptTimeLimit(@Nonnull TimeLimiter timeLimiter, long duration, @Nonnull TimeUnit timeUnit) {
                Preconditions.checkNotNull(timeLimiter);
                Preconditions.checkNotNull(timeUnit);
                this.timeLimiter = timeLimiter;
                this.duration = duration;
                this.timeUnit = timeUnit;
            }

            @Override
            public V call(Callable<V> callable) throws Exception {
                return timeLimiter.callWithTimeout(callable, duration, timeUnit);
            }
        }
    }


}
