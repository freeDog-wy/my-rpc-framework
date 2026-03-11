package site.elseif.myRpcFramework.core.flow;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 流量控制器
 * 支持服务级别和方法级别的限流
 */
@Slf4j
public class FlowController {

    // 服务级别限流器
    private final Map<String, RateLimiter> serviceLimiters = new ConcurrentHashMap<>();

    // 方法级别限流器
    private final Map<String, RateLimiter> methodLimiters = new ConcurrentHashMap<>();


    public boolean tryAcquire(String serviceName, String methodName) {
        String methodKey = serviceName + "#" + methodName;
        RateLimiter methodLimiter = methodLimiters.get(methodKey);
        if (methodLimiter != null && !methodLimiter.tryAcquire()) {
            log.warn("方法限流触发：{}", methodKey);
            return false;
        }

        return true;
    }
    /**
     * 服务端限流 - 处理请求前调用
     */
    public boolean tryAcquireForServer(String serviceName, String methodName) {
        // 先检查方法级别限流
        String methodKey = serviceName + "#" + methodName;
        RateLimiter methodLimiter = methodLimiters.get(methodKey);
        if (methodLimiter != null && !methodLimiter.tryAcquire()) {
            log.warn("方法限流触发：{}", methodKey);
            return false;
        }

        // 再检查服务级别限流
//        RateLimiter serviceLimiter = serviceLimiters.get(serviceName);
//        if (serviceLimiter != null && !serviceLimiter.tryAcquire()) {
//            log.warn("服务限流触发：{}", serviceName);
//            return false;
//        }

        return true;
    }

    /**
     * 客户端限流 - 发送请求前调用
     */
    public boolean tryAcquireForClient(String serviceName) {
        RateLimiter limiter = serviceLimiters.get(serviceName);
        if (limiter == null) {
            return true;
        }
        return limiter.tryAcquire();
    }

    /**
     * 配置服务限流规则
     */
    public void setServiceRule(String serviceName, long qps) {
        serviceLimiters.put(serviceName, new RateLimiter(qps, qps));
        log.info("配置服务限流：{} -> {} QPS", serviceName, qps);
    }

    /**
     * 配置方法限流规则
     */
    public void setMethodRule(String serviceName, String methodName, long qps) {
        String key = serviceName + "#" + methodName;
        methodLimiters.put(key, new RateLimiter(qps, qps));
        log.info("应用配置方法限流：{} -> {} QPS", key, qps);
    }
}
