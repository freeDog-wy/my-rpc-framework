package site.elseif.myRpcFramework.common.exception;

/**
 * 熔断异常
 */
public class CircuitBreakerException extends RpcException {

    public CircuitBreakerException(String serviceName, String methodName) {
        super(RpcErrorCode.PROVIDER_CIRCUIT_BREAKER,
                String.format("服务[%s#%s]已熔断", serviceName, methodName),
                serviceName, methodName);
    }

    public CircuitBreakerException(boolean isProvider, String serviceName, String methodName) {
        super(isProvider ? RpcErrorCode.PROVIDER_CIRCUIT_BREAKER : RpcErrorCode.CONSUMER_CIRCUIT_BREAKER,
                String.format("%s端熔断: %s#%s", isProvider ? "服务" : "客户", serviceName, methodName),
                serviceName, methodName);
    }
}