package site.elseif.myRpcFramework.core.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import lombok.extern.slf4j.Slf4j;
import site.elseif.myRpcFramework.common.MessageConstants;
import site.elseif.myRpcFramework.common.MessageHeader;
import site.elseif.myRpcFramework.common.RpcProtocol;
import site.elseif.myRpcFramework.common.exception.RpcErrorCode;
import site.elseif.myRpcFramework.common.exception.RpcException;
import site.elseif.myRpcFramework.core.serializer.SerializerFactory;
import site.elseif.myRpcFramework.core.serializer.Serializer;

import java.nio.charset.StandardCharsets;

@Slf4j
public class RpcMessageEncoder extends MessageToByteEncoder<RpcProtocol<Object>> {

    @Override
    protected void encode(ChannelHandlerContext ctx, RpcProtocol<Object> msg, ByteBuf out) {
        try {
            // 1. 获取消息头和消息体
            MessageHeader header = msg.getHeader();
            Object body = msg.getBody();

            // 2. 序列化消息体
            Serializer serializer = SerializerFactory.getSerializer(header.getSerializerType());
            byte[] bodyBytes = serializer.serialize(body);

            if (bodyBytes.length + 43 > MessageConstants.MAX_FRAME_LENGTH) {
                log.error("消息体过大，长度：{}", bodyBytes.length);
                throw new RpcException(
                        RpcErrorCode.SERIALIZE_ERROR
                        , "消息体过大，长度："
                            + bodyBytes.length
                            + "，最大允许长度："
                            + (MessageConstants.MAX_FRAME_LENGTH - 43)
                );
            }

            // 3. 更新数据长度
            header.setDataLength(bodyBytes.length);

            // 4. 写入魔数
            out.writeInt(MessageConstants.MAGIC_NUMBER);

            // 5. 写入版本号
            out.writeByte(header.getVersion());

            // 6. 写入消息类型
            out.writeByte(header.getMessageType());

            // 7. 写入序列化方式
            out.writeByte(header.getSerializerType());

            // 8. 写入消息ID（固定32字节）
            byte[] messageIdBytes = new byte[32];
            byte[] idBytes = header.getMessageId().getBytes(StandardCharsets.UTF_8);
            System.arraycopy(idBytes, 0, messageIdBytes, 0, Math.min(idBytes.length, 32));
            out.writeBytes(messageIdBytes);

            // 9. 写入数据长度
            out.writeInt(bodyBytes.length);

            // 10. 写入数据体
            out.writeBytes(bodyBytes);

            log.debug("消息编码完成，消息ID：{}，数据长度：{}", header.getMessageId(), bodyBytes.length);

        } catch (Exception e) {
            log.error("消息编码失败", e);
            throw new RuntimeException("消息编码失败", e);
        }
    }
}