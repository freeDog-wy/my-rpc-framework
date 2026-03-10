package site.elseif.myRpcFramework.core.serializer;

public interface Serializer {
    /**
     * 获取序列化类型（对应MessageConstants中的常量）
     */
    byte getSerializerType();
    byte[] serialize(Object obj);
    <T> T deserialize(byte[] bytes, Class<T> clazz);
}