package site.elseif.myRpcFramework.common;

import lombok.Data;

import java.io.Serializable;

@Data
public class RpcProtocol<T> implements Serializable {
    private MessageHeader header;  // 消息头
    private T body;                // 消息体（RpcRequest或RpcResponse）
}