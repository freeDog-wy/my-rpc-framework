package site.elseif.myRpcFramework.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessageHeader implements Serializable {
    private int magic;           // 魔数
    private byte version;         // 版本号
    private byte messageType;     // 消息类型
    private byte serializerType;  // 序列化方式
    private String messageId;     // 消息ID (32字节，用UUID字符串)
    private int dataLength;       // 数据体长度
}