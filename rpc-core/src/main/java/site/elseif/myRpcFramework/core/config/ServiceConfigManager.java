package site.elseif.myRpcFramework.core.config;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import site.elseif.myRpcFramework.common.group.ServiceGroup;
import site.elseif.myRpcFramework.core.circuit.CircuitBreakerManager;
import site.elseif.myRpcFramework.core.flow.FlowController;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务配置管理器
 */
@Slf4j
public class ServiceConfigManager {

    // 服务配置映射
    private final Map<String, ServiceConfig> configMap = new ConcurrentHashMap<>();

    // 限流控制器
    @Getter
    private final FlowController flowController;

    // 熔断器管理器
    @Getter
    private final CircuitBreakerManager circuitBreakerManager;

    // 服务分组
    @Getter
    private final ServiceGroup serviceGroup;

    public ServiceConfigManager() {
        this.flowController = new FlowController();
        this.circuitBreakerManager = new CircuitBreakerManager();
        this.serviceGroup = new ServiceGroup();
    }

    /**
     * 创建或获取服务配置
     */
    public ServiceConfig getOrCreateConfig(String serviceName) {
        return configMap.computeIfAbsent(serviceName, k -> {
            ServiceConfig config = new ServiceConfig(serviceName);
            log.info("创建服务配置：{}", serviceName);
            return config;
        });
    }

    /**
     * 配置服务分组
     */
    public ServiceConfigManager setGroup(String serviceName, ServiceGroup.Group group) {
        ServiceConfig config = getOrCreateConfig(serviceName);
        config.setGroup(group);
        return this;
    }

    /**
     * 配置方法限流
     */
    public ServiceConfigManager limitMethod(String serviceName, String methodName, long qps) {
        ServiceConfig config = getOrCreateConfig(serviceName);
        config.limitMethod(methodName, qps);

        log.info("配置方法限流：{}#{} -> {} QPS", serviceName, methodName, qps);
        return this;
    }

    /**
     * 配置方法熔断
     */
    public ServiceConfigManager circuitBreakMethod(String serviceName,
                                                   String methodName,
                                                   int failureThreshold,
                                                   long timeoutMs,
                                                   int halfOpenSuccess) {
        ServiceConfig config = getOrCreateConfig(serviceName);
        config.circuitBreakMethod(methodName, failureThreshold, timeoutMs, halfOpenSuccess);

        log.info("配置方法熔断：{}#{} 失败阈值={}, 超时={}ms",
                serviceName, methodName, failureThreshold, timeoutMs);
        return this;
    }

    /**
     * 应用所有配置（启动时调用）
     */
    public void applyConfigs() {
        log.info("开始应用：限流服务配置，分组配置，熔断器会在第一次调用时创建");
        configMap.forEach((serviceName, config) -> {
            // 应用服务分组
            serviceGroup.setGroup(serviceName, config.getGroup());

            // 应用方法配置
            config.getMethodConfigs().forEach((methodName, methodConfig) -> {
                if (methodConfig.getFlowLimitQps() != null) {
                    flowController.setMethodRule(serviceName, methodName,
                            methodConfig.getFlowLimitQps());
                }

                // 熔断器会在第一次使用时自动创建
            });
        });

        log.info("所有服务配置已应用");
    }
}