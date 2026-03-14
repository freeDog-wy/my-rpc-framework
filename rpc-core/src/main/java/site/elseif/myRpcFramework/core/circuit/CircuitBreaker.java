package site.elseif.myRpcFramework.core.circuit;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 线程安全的熔断器实现
 */
@Slf4j
public class CircuitBreaker {

    public enum State {
        CLOSED, OPEN, HALF_OPEN
    }

    private final String key;
    private final int failureThreshold;
    private final long baseTimeoutMs;
    private final long maxTimeoutMs = 5 * 60 * 1000;
    private final int halfOpenSuccessThreshold;
    private final int maxBackoffAttempts;

    // 使用 AtomicReference 保证状态更新的原子性
    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);

    // 使用 Atomic 类型保证计数器的线程安全
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicLong lastFailureTime = new AtomicLong(0);
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);

    // 使用读写锁优化并发性能
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    public CircuitBreaker(String key, int failureThreshold, long baseTimeoutMs, int halfOpenSuccessThreshold) {
        this.key = key;
        this.failureThreshold = failureThreshold;
        this.baseTimeoutMs = baseTimeoutMs;
        this.halfOpenSuccessThreshold = halfOpenSuccessThreshold;
        this.maxBackoffAttempts = 5;
    }

    /**
     * 是否允许请求通过（读操作使用读锁）
     */
    public boolean allowRequest() {
        // 先快速检查，避免不必要的锁竞争
        State currentState = state.get();

        if (currentState == State.CLOSED) {
            return true;
        }

        if (currentState == State.OPEN) {
            // 使用读锁检查是否需要转换到 HALF_OPEN
            lock.readLock().lock();
            try {
                long waitTime = calculateBackoffTime();
                if (System.currentTimeMillis() - lastFailureTime.get() > waitTime) {
                    // 需要转换状态，升级到写锁
                    lock.readLock().unlock();
                    lock.writeLock().lock();
                    try {
                        // 双重检查，防止其他线程已经转换
                        if (state.get() == State.OPEN &&
                                System.currentTimeMillis() - lastFailureTime.get() > waitTime) {
                            transitionToHalfOpen();
                        }
                        return state.get() != State.OPEN;
                    } finally {
                        lock.writeLock().unlock();
                        lock.readLock().lock(); // 重新获取读锁以保持平衡
                    }
                }
                return false;
            } finally {
                lock.readLock().unlock();
            }
        }

        // HALF_OPEN 状态
        if (currentState == State.HALF_OPEN) {
            return successCount.get() < halfOpenSuccessThreshold;
        }

        return false;
    }

    /**
     * 记录成功调用（使用写锁）
     */
    public void recordSuccess() {
        lock.writeLock().lock();
        try {
            State currentState = state.get();

            if (currentState == State.HALF_OPEN) {
                int success = successCount.incrementAndGet();
                if (success >= halfOpenSuccessThreshold) {
                    transitionToClosed();
                }
            } else if (currentState == State.CLOSED) {
                failureCount.set(0);
                consecutiveFailures.set(0);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 记录失败调用（使用写锁）
     */
    public void recordFailure() {
        lock.writeLock().lock();
        try {
            lastFailureTime.set(System.currentTimeMillis());
            consecutiveFailures.incrementAndGet();

            State currentState = state.get();

            switch (currentState) {
                case CLOSED:
                    int failures = failureCount.incrementAndGet();
                    if (failures >= failureThreshold) {
                        transitionToOpen();
                    }
                    break;

                case HALF_OPEN:
                    transitionToOpen();
                    break;

                case OPEN:
                    // 已经熔断，只更新失败时间
                    break;
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    private long calculateBackoffTime() {
        int attempts = Math.min(consecutiveFailures.get(), maxBackoffAttempts);
        long timeout = baseTimeoutMs * (1L << attempts);
        return Math.min(timeout, maxTimeoutMs);
    }

    private void transitionToOpen() {
        if (state.compareAndSet(State.CLOSED, State.OPEN) ||
                state.compareAndSet(State.HALF_OPEN, State.OPEN)) {
            successCount.set(0);
            log.warn("熔断器开启：{}，失败次数：{}，连续失败次数：{}",
                    key, failureCount.get(), consecutiveFailures.get());
        }
    }

    private void transitionToHalfOpen() {
        if (state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
            successCount.set(0);
            log.info("熔断器半开：{}，尝试恢复，当前连续失败次数：{}",
                    key, consecutiveFailures.get());
        }
    }

    private void transitionToClosed() {
        if (state.compareAndSet(State.HALF_OPEN, State.CLOSED)) {
            failureCount.set(0);
            successCount.set(0);
            consecutiveFailures.set(0);
            log.info("熔断器关闭：{}，恢复正常", key);
        }
    }

    /**
     * 获取当前状态（无锁，适用于监控）
     */
    public State getState() {
        return state.get();
    }

    /**
     * 获取状态信息（使用读锁保证一致性）
     */
    public String getStateInfo() {
        lock.readLock().lock();
        try {
            return String.format(
                    "CircuitBreaker{key='%s', state=%s, failures=%d, consecutiveFailures=%d, waitTime=%dms}",
                    key, state.get(), failureCount.get(), consecutiveFailures.get(),
                    state.get() == State.OPEN ? calculateBackoffTime() : 0
            );
        } finally {
            lock.readLock().unlock();
        }
    }
}