# spring retry and guava retry demo

从spring retry 官方copy:
To make processing more robust and less prone to failure, it sometimes helps to automatically retry a failed operation, in case it might succeed on a subsequent attempt. Errors that are susceptible to this kind of treatment are transient in nature. For example, a remote call to a web service or an RMI service that fails because of a network glitch or a DeadLockLoserException in a database update may resolve itself after a short wait. To automate the retry of such operations, Spring Retry has the RetryOperations strategy. 

为了使处理更加健壮，减少失败的可能性，有时候自动重试失败的操作。
目前有两个开源的类库 spring retry and guava retry 都支持编程式，spring retry 更加融合spring的aop 注解驱动，使用更加的方便。

## 模拟异常场景

为了实现demo，模拟了异常的场景
* 异常调用
```text
http://localhost:8080/unstableApi/500
```
* 调用正常
```text
http://localhost:8080/unstableApi/200
```
```java
@RestController
public class MockApiController {

    /**
     * 模拟服务不正常
     *
     * @param status
     * @return
     */
    @GetMapping("/unstableApi/{status}")
    public ResponseEntity<Integer> unstableApi(@PathVariable int status) {
        if (INTERNAL_SERVER_ERROR.value() == status) {
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR);
        }
        if (UNAUTHORIZED.value() == status) {
            throw new ResponseStatusException(UNAUTHORIZED);
        }
        return ResponseEntity.ok(status);
    }

}
```

## 实践

spring retry支持有状态和无状态两种方式。一般理解使用无状态。
* 无状态:
无状态就是当前线程继续处理，spring retry 通过获取到异常后继续在当前线程重试。

* 有状态: 
类比http,http 调用是无状态的，为了增加访问状态可能增加cookie 标识一个人的访问，当前的多次访问是否是一个人;
spring retry 中有状态我这么理解,多次调用不直接的在当前线程重试，将异常抛出,标识为【当前方法参数+方法名称】,记录下当前失败的key对应的记录。
下一次在继续调用对于相同的key可以进行失败统计，如果达到目标失败次数，会调用失败处理的兜底回调org.springframework.retry.RecoveryCallback 进行记录。
[spring retry 中的 stateful 如何使用？](https://www.zhihu.com/question/265289234/answer/952517156)

配置开关进行测试,要测试 将开关配置为true 即可观察日志!
```properties
#com.wangji92.retry.springretryexample.task.SchedulingTestRetryTask
#测试 retry的效果 是通过编程的方式 、还是通过 aop注解的方式测试

aopSpringRetry=true
programmingSpringRetry=false
programmingGuavaRetry=false

# 有状态的重试  com.wangji92.retry.springretryexample.task.SchedulingTestStateFullRetryTask
aopStateFullRetry=false
programmingStateFullRetry=false
```
所有的场景都是通过定时器进行调用模拟,具体使用可以参考链接

* aopSpringRetry  aop 实践无状态重试
[SchedulingTestRetryTask#AopSpringRetry](https://github.com/WangJi92/spring-retry-example/blob/master/src/main/java/com/wangji92/retry/springretryexample/task/SchedulingTestRetryTask.java)
* programmingSpringRetry 编程实践spring retry
[SchedulingTestRetryTask#ProgrammingSpringRetry](https://github.com/WangJi92/spring-retry-example/blob/master/src/main/java/com/wangji92/retry/springretryexample/task/SchedulingTestRetryTask.java)
* programmingGuavaRetry 编程实践guava retry
[SchedulingTestRetryTask#ProgrammingGuavaRetry](https://github.com/WangJi92/spring-retry-example/blob/master/src/main/java/com/wangji92/retry/springretryexample/task/SchedulingTestRetryTask.java)
* aopStateFullRetry  aop 实践spring retry 有状态
[SchedulingTestStateFullRetryTask#AopStateFullRetry](https://github.com/WangJi92/spring-retry-example/blob/master/src/main/java/com/wangji92/retry/springretryexample/task/SchedulingTestStateFullRetryTask.java)
* programmingStateFullRetry 编程实践spring retry 有状态
[SchedulingTestStateFullRetryTask#ProgrammingStateFullRetry](https://github.com/WangJi92/spring-retry-example/blob/master/src/main/java/com/wangji92/retry/springretryexample/task/SchedulingTestStateFullRetryTask.java)

## 配置
### spring retry
@EnableRetry(proxyTargetClass = true)

```xml
<dependency>
    <groupId>org.springframework.retry</groupId>
    <artifactId>spring-retry</artifactId>
    <version>1.3.1</version>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aop</artifactId>
</dependency>
```

### guava retry

```xml
<dependency>
    <groupId>com.github.rholder</groupId>
    <artifactId>guava-retrying</artifactId>
    <version>2.0.0</version>
</dependency>
```
withAttemptTimeLimiter 会导致执行业务的线程和调用线程非一个线程执行，会带来事务问题、线程上下文传递问题
还有一个问题 guava-retrying 对于最新版本的guava 没有支持 [https://github.com/rholder/guava-retrying/issues/66](https://github.com/rholder/guava-retrying/issues/66)
使用的时候一定要小心啦，默认使用最新版本的guava,不推荐使用。

```java
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
```
## spring retry 框架的理解
[spring retry 框架的理解 博客](spirng-retry-understand.md)

## 参考文档
* [spring retry](https://github.com/spring-projects/spring-retry)

* [guava-retry](https://github.com/rholder/guava-retrying)
