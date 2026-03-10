package site.elseif.myRpcFramework.common;

import lombok.Data;

import java.io.Serializable;

@Data
public class RpcRequest implements Serializable {
    private String requestId;          // 请求ID
    private String interfaceName;       // 接口名
    private String methodName;          // 方法名
    private Class<?>[] parameterTypes;  // 参数类型
    private Object[] parameters;        // 参数值
}