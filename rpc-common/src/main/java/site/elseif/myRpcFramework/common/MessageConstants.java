package site.elseif.myRpcFramework.common;

public class MessageConstants {
    public static final int MAGIC_NUMBER = 0x12345678;  // 魔数，用于快速校验
    public static final byte VERSION = 1;                // 协议版本号

    // 消息头长度：魔数(4) + 版本(1) + 类型(1) + 序列化方式(1) + 消息ID(32) + 数据长度(4) = 43字节
    public static final int HEADER_LENGTH = 43;

    public static final int MAX_FRAME_LENGTH = 8 * 1024 * 1024; // 最大帧长度，防止内存溢出

    // 消息类型
    public static final byte REQUEST = 0;
    public static final byte RESPONSE = 1;
    public static final byte HEARTBEAT = 2;

    // 序列化方式
    public static final byte SERIALIZER_KRYO = 0;
    public static final byte SERIALIZER_PROTOBUF = 1;
    public static final byte SERIALIZER_HESSIAN = 2;
}