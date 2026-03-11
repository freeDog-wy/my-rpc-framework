package site.elseif.myRpcFramework.common.exception;

import lombok.Getter;

/**
 * RPC错误码定义
 */
@Getter
public enum RpcErrorCode {

    // 限流相关 (1000-1999)
    PROVIDER_FLOW_LIMIT(1001, "服务端限流", true),      // true表示可重试
    CONSUMER_FLOW_LIMIT(1002, "客户端限流", false),    // false表示不可重试

    // 熔断相关 (2000-2999)
    PROVIDER_CIRCUIT_BREAKER(2001, "服务端熔断", false),
    CONSUMER_CIRCUIT_BREAKER(2002, "客户端熔断", false),

    // 服务发现相关 (3000-3999)
    SERVICE_NOT_FOUND(3001, "服务不存在", true),
    INSTANCE_NOT_AVAILABLE(3002, "无可用的服务实例", true),

    // 网络相关 (4000-4999)
    REQUEST_TIMEOUT(4001, "请求超时", true),
    CONNECTION_ERROR(4002, "连接异常", true),

    // 序列化相关 (5000-5999)
    SERIALIZE_ERROR(5001, "序列化失败", false),
    DESERIALIZE_ERROR(5002, "反序列化失败", false),

    // 业务相关 (6000-6999)
    METHOD_NOT_FOUND(6001, "方法不存在", false),
    INVOCATION_ERROR(6002, "方法调用失败", false);

    private final int code;
    private final String message;
    private final boolean retryable;  // 是否可重试

    RpcErrorCode(int code, String message, boolean retryable) {
        this.code = code;
        this.message = message;
        this.retryable = retryable;
    }

    public static RpcErrorCode fromCode(int code) {
        for (RpcErrorCode errorCode : values()) {
            if (errorCode.code == code) {
                return errorCode;
            }
        }
        return null;
    }
}
