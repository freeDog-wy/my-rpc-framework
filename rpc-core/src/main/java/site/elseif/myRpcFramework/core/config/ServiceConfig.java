package site.elseif.myRpcFramework.core.config;

import lombok.Data;
import site.elseif.myRpcFramework.common.group.ServiceGroup;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务级别的配置
 */
@Data
public class ServiceConfig {

    // 服务名称
    private String serviceName;

    // 服务分组
    private ServiceGroup.Group group = ServiceGroup.Group.BUSINESS;

    // 方法级别的配置
    private final Map<String, MethodConfig> methodConfigs = new ConcurrentHashMap<>();

    // 默认方法配置（当方法没有单独配置时使用）
    private MethodConfig defaultMethodConfig = MethodConfig.defaultConfig();

    public ServiceConfig(String serviceName) {
        this.serviceName = serviceName;
    }

    /**
     * 配置具体方法的熔断和限流
     */
    public ServiceConfig configMethod(String methodName, MethodConfig config) {
        methodConfigs.put(methodName, config);
        return this;
    }

    /**
     * 配置方法限流
     */
    public ServiceConfig limitMethod(String methodName, long qps) {
        MethodConfig config = methodConfigs.computeIfAbsent(methodName,
                k -> MethodConfig.defaultConfig());
        config.setFlowLimitQps(qps);
        return this;
    }

    /**
     * 配置方法熔断
     */
    public ServiceConfig circuitBreakMethod(String methodName,
                                            int failureThreshold,
                                            long timeoutMs,
                                            int halfOpenSuccess) {
        MethodConfig config = methodConfigs.computeIfAbsent(methodName,
                k -> MethodConfig.defaultConfig());
        config.setCircuitBreakerFailureThreshold(failureThreshold);
        config.setCircuitBreakerTimeoutMs(timeoutMs);
        config.setCircuitBreakerHalfOpenSuccess(halfOpenSuccess);
        return this;
    }

    /**
     * 获取方法的配置
     */
    public MethodConfig getMethodConfig(String methodName) {
        return methodConfigs.getOrDefault(methodName, defaultMethodConfig);
    }
}