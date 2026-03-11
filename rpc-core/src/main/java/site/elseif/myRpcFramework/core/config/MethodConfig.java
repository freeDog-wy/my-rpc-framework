package site.elseif.myRpcFramework.core.config;

import lombok.Builder;
import lombok.Data;

/**
 * 方法级别的配置
 */
@Data
@Builder
public class MethodConfig {

    // 熔断配置
    private Integer circuitBreakerFailureThreshold;  // 失败阈值
    private Long circuitBreakerTimeoutMs;            // 熔断超时时间
    private Integer circuitBreakerHalfOpenSuccess;   // 半开成功阈值

    // 限流配置
    private Long flowLimitQps;                        // 限流QPS

    // 是否启用
    private boolean enabled = true;

    // 默认配置
    public static MethodConfig defaultConfig() {
        return MethodConfig.builder()
                .circuitBreakerFailureThreshold(5)
                .circuitBreakerTimeoutMs(10000L)
                .circuitBreakerHalfOpenSuccess(3)
                .flowLimitQps(1000L)
                .build();
    }
}