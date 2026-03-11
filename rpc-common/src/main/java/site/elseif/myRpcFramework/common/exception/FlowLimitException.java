package site.elseif.myRpcFramework.common.exception;

/**
 * 限流异常
 */
public class FlowLimitException extends RpcException {

    public FlowLimitException(String serviceName, String methodName) {
        super(RpcErrorCode.PROVIDER_FLOW_LIMIT,
                String.format("服务[%s#%s]触发限流", serviceName, methodName),
                serviceName, methodName);
    }

    public FlowLimitException(boolean isProvider, String serviceName, String methodName) {
        super(isProvider ? RpcErrorCode.PROVIDER_FLOW_LIMIT : RpcErrorCode.CONSUMER_FLOW_LIMIT,
                String.format("%s端限流: %s#%s", isProvider ? "服务" : "客户", serviceName, methodName),
                serviceName, methodName);
    }
}