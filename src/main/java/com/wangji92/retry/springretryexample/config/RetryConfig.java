package com.wangji92.retry.springretryexample.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.interceptor.MethodInvocationRetryCallback;
import org.springframework.retry.listener.MethodInvocationRetryListenerSupport;

/**
 * @author 汪小哥
 * @date 02-04-2021
 */
@Slf4j
@Configuration
@EnableRetry(proxyTargetClass = true)
public class RetryConfig {

    /**
     * 非必须的 监听器
     *
     * @return
     */
    @Bean
    public RetryListener retryListenerCustom() {
        return new MethodInvocationRetryListenerSupport() {

            @Override
            protected <T, E extends Throwable> boolean doOpen(RetryContext context, MethodInvocationRetryCallback<T, E> callback) {
                log.info("retry doOpen count={} method name={}", context.getRetryCount(), callback.getInvocation().getMethod().getName());
                return super.doOpen(context, callback);
            }

            @Override
            protected <T, E extends Throwable> void doClose(RetryContext context, MethodInvocationRetryCallback<T, E> callback, Throwable throwable) {
                log.info("retry doClose count={} method name={}", context.getRetryCount(), callback.getInvocation().getMethod().getName());
            }

            @Override
            protected <T, E extends Throwable> void doOnError(RetryContext context, MethodInvocationRetryCallback<T, E> callback, Throwable throwable) {
                log.info("retry doOnError count={} method name={}", context.getRetryCount(), callback.getInvocation().getMethod().getName());
            }
        };

    }

}

