package site.elseif.myRpcFramework.core.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import site.elseif.myRpcFramework.common.*;
import site.elseif.myRpcFramework.core.serializer.SerializerFactory;
import site.elseif.myRpcFramework.core.serializer.Serializer;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RpcMessageDecoder extends LengthFieldBasedFrameDecoder {

    public RpcMessageDecoder() {
        /**
         * 参数说明：
         * maxFrameLength: 最大帧长度，防止内存溢出
         * lengthFieldOffset: 长度字段的偏移量（从第几个字节开始是长度字段）
         *   魔数4 + 版本1 + 类型1 + 序列化方式1 + 消息ID32 = 39
         * lengthFieldLength: 长度字段的长度（int类型占4字节）
         * lengthAdjustment: 长度调整值（长度字段之后还有多少字节才是完整的数据）
         * initialBytesToStrip: 解码后跳过的字节数（这里我们不跳过头部，因为后面要用）
         */
        super(8 * 1024 * 1024, 39, 4, 0, 0);
    }

    @Override
    protected Object decode(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
        log.debug("开始解码消息，当前可读字节数：{}", in.readableBytes());
        // 调用父类的decode方法，解决粘包拆包
        ByteBuf frame = (ByteBuf) super.decode(ctx, in);
        if (frame == null) {
            return null;
        }

        try {
            // 1. 读取魔数（快速校验）
            int magic = frame.readInt();
            if (magic != MessageConstants.MAGIC_NUMBER) {
                log.error("魔数校验失败，关闭连接");
                ctx.close();
                return null;
            }

            // 2. 读取协议版本
            byte version = frame.readByte();
            if (version > MessageConstants.VERSION) {
                log.error("协议版本不兼容：{}", version);
                ctx.close();
                return null;
            }

            // 3. 读取消息类型
            byte messageType = frame.readByte();

            // 4. 读取序列化方式
            byte serializerType = frame.readByte();

            // 5. 读取消息ID（32字节）
            byte[] messageIdBytes = new byte[32];
            frame.readBytes(messageIdBytes);
            String messageId = new String(messageIdBytes, "UTF-8").trim();

            // 6. 读取数据长度
            int dataLength = frame.readInt();

            // 7. 构建消息头
            MessageHeader header = new MessageHeader();
            header.setMagic(magic);
            header.setVersion(version);
            header.setMessageType(messageType);
            header.setSerializerType(serializerType);
            header.setMessageId(messageId);
            header.setDataLength(dataLength);

            // 8. 读取数据体
            byte[] data = new byte[dataLength];
            frame.readBytes(data);

            // 9. 反序列化
            Serializer serializer = SerializerFactory.getSerializer(serializerType);
            Class<?> clazz = messageType == MessageConstants.REQUEST ?
                    RpcRequest.class : RpcResponse.class;
            Object body = serializer.deserialize(data, clazz);

            // 10. 封装成协议对象
            RpcProtocol<Object> protocol = new RpcProtocol<>();
            protocol.setHeader(header);
            protocol.setBody(body);
            log.debug("消息解码完成，消息ID：{}，数据长度：{}", header.getMessageId(), dataLength);
            return protocol;

        } finally {
            // 释放frame
            frame.release();
        }
    }
}