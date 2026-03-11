package site.elseif.myRpcFramework.core.circuit;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 熔断器实现
 * 状态机：CLOSED -> OPEN -> HALF_OPEN -> CLOSED
 */
@Slf4j
public class CircuitBreaker {

    // 熔断器状态
    public enum State {
        CLOSED,     // 关闭状态（正常工作）
        OPEN,       // 开启状态（熔断）
        HALF_OPEN   // 半开状态（尝试恢复）
    }

    private final String key;                    // 熔断器标识（服务#方法）
    private final int failureThreshold;           // 失败阈值
    private final long timeoutMs;                 // 熔断超时时间
    private final int halfOpenSuccessThreshold;   // 半开状态成功阈值

    @Getter
    private State state = State.CLOSED;
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicLong lastFailureTime = new AtomicLong(0);

    public CircuitBreaker(String key, int failureThreshold, long timeoutMs, int halfOpenSuccessThreshold) {
        this.key = key;
        this.failureThreshold = failureThreshold;
        this.timeoutMs = timeoutMs;
        this.halfOpenSuccessThreshold = halfOpenSuccessThreshold;
    }

    /**
     * 是否允许请求通过
     */
    public synchronized boolean allowRequest() {
        switch (state) {
            case CLOSED:
                return true;

            case OPEN:
                // 检查是否达到超时时间
                if (System.currentTimeMillis() - lastFailureTime.get() > timeoutMs) {
                    // 进入半开状态
                    transitionToHalfOpen();
                    return true;
                }
                log.debug("熔断器开启，请求被拒绝：{}", key);
                return false;

            case HALF_OPEN:
                // 半开状态允许部分请求通过
                return successCount.get() < halfOpenSuccessThreshold;

            default:
                return false;
        }
    }

    /**
     * 记录成功调用
     */
    public synchronized void recordSuccess() {
        if (state == State.HALF_OPEN) {
            int success = successCount.incrementAndGet();
            if (success >= halfOpenSuccessThreshold) {
                // 半开状态连续成功达到阈值，关闭熔断器
                transitionToClosed();
            }
        } else if (state == State.CLOSED) {
            // 关闭状态成功，重置失败计数
            failureCount.set(0);
        }
    }

    /**
     * 记录失败调用
     */
    public synchronized void recordFailure() {
        lastFailureTime.set(System.currentTimeMillis());

        switch (state) {
            case CLOSED:
                int failures = failureCount.incrementAndGet();
                if (failures >= failureThreshold) {
                    // 达到失败阈值，开启熔断
                    transitionToOpen();
                }
                break;

            case HALF_OPEN:
                // 半开状态失败，立即熔断
                transitionToOpen();
                break;

            case OPEN:
                // 已经熔断，不需要处理
                break;
        }
    }

    private void transitionToOpen() {
        state = State.OPEN;
        successCount.set(0);
        log.warn("熔断器开启：{}，失败次数：{}", key, failureCount.get());
    }

    private void transitionToHalfOpen() {
        state = State.HALF_OPEN;
        successCount.set(0);
        log.info("熔断器半开：{}，尝试恢复", key);
    }

    private void transitionToClosed() {
        state = State.CLOSED;
        failureCount.set(0);
        successCount.set(0);
        log.info("熔断器关闭：{}，恢复正常", key);
    }

}