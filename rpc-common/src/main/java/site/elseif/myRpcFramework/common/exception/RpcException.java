package site.elseif.myRpcFramework.common.exception;

import lombok.Getter;

/**
 * RPC框架基础异常类
 */
@Getter
public class RpcException extends RuntimeException {

    private final RpcErrorCode code;
    private final String serviceName;
    private final String methodName;

    public RpcException(RpcErrorCode code, String message) {
        super(message);
        this.code = code;
        this.serviceName = null;
        this.methodName = null;
    }

    public RpcException(RpcErrorCode code, String message,
                        String serviceName, String methodName) {
        super(message);
        this.code = code;
        this.serviceName = serviceName;
        this.methodName = methodName;
    }

    public RpcException(RpcErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.serviceName = null;
        this.methodName = null;
    }
}
