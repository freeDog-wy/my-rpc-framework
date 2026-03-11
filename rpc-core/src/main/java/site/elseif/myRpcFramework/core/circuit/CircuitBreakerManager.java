package site.elseif.myRpcFramework.core.circuit;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 熔断器管理器
 */
@Slf4j
public class CircuitBreakerManager {

    private final Map<String, CircuitBreaker> breakers = new ConcurrentHashMap<>();

    // 默认配置
    private static final int DEFAULT_FAILURE_THRESHOLD = 5;
    private static final long DEFAULT_TIMEOUT_MS = 10000;
    private static final int DEFAULT_HALF_OPEN_SUCCESS_THRESHOLD = 3;

    /**
     * 获取熔断器
     */
    public CircuitBreaker getBreaker(String serviceName, String methodName) {
        String key = serviceName + "#" + methodName;
        return breakers.computeIfAbsent(key, k -> new CircuitBreaker(
                key,
                DEFAULT_FAILURE_THRESHOLD,
                DEFAULT_TIMEOUT_MS,
                DEFAULT_HALF_OPEN_SUCCESS_THRESHOLD
        ));
    }

    /**
     * 自定义配置的熔断器
     */
    public CircuitBreaker getBreaker(String serviceName, String methodName,
                                     int failureThreshold, long timeoutMs,
                                     int halfOpenSuccessThreshold) {
        String key = serviceName + "#" + methodName;
        return breakers.computeIfAbsent(key, k -> new CircuitBreaker(
                key, failureThreshold, timeoutMs, halfOpenSuccessThreshold
        ));
    }

    /**
     * 获取所有熔断器状态
     */
    public Map<String, CircuitBreaker.State> getAllStates() {
        Map<String, CircuitBreaker.State> states = new ConcurrentHashMap<>();
        breakers.forEach((key, breaker) -> states.put(key, breaker.getState()));
        return states;
    }
}