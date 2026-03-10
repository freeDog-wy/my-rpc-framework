package site.elseif.myRpcFramework.common;

import lombok.Data;

import java.io.Serializable;

@Data
public class RpcResponse implements Serializable {
    private String requestId;           // 请求ID（与请求对应）
    private Object result;              // 返回结果
    private Throwable error;             // 错误信息
}