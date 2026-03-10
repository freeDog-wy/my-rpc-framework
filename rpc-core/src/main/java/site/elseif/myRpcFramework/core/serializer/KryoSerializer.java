package site.elseif.myRpcFramework.core.serializer;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import site.elseif.myRpcFramework.common.MessageConstants;
import site.elseif.myRpcFramework.common.RpcProtocol;
import site.elseif.myRpcFramework.common.RpcRequest;
import site.elseif.myRpcFramework.common.RpcResponse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class KryoSerializer implements Serializer {

    /**
     * 使用ThreadLocal保证每个线程都有自己的Kryo实例
     * 因为Kryo不是线程安全的
     */
    private static final ThreadLocal<Kryo> kryoThreadLocal = ThreadLocal.withInitial(() -> {
        Kryo kryo = new Kryo();

        // 1. 启用引用机制，支持循环引用（比如A引用B，B引用A）
        kryo.setReferences(true);

        // 2. 设置注册模式为：如果需要注册类，必须显式注册
        kryo.setRegistrationRequired(false);  // false表示可以动态注册类

        // 3. 注册常用的类，提高性能
        registerCommonClasses(kryo);

        return kryo;
    });

    /**
     * 注册RPC框架中常用的类
     */
    private static void registerCommonClasses(Kryo kryo) {
        // 注册RPC相关的类
        kryo.register(RpcRequest.class);
        kryo.register(RpcResponse.class);
        kryo.register(RpcProtocol.class);

        // 注册数组类型
        kryo.register(Object[].class);
        kryo.register(Class[].class);
        kryo.register(String[].class);

        // 注册常用的集合类
        kryo.register(java.util.ArrayList.class);
        kryo.register(java.util.HashMap.class);
        kryo.register(java.util.HashSet.class);

        // 注意：具体的业务参数类型会在运行时动态注册
    }

    @Override
    public byte getSerializerType() {
        return MessageConstants.SERIALIZER_KRYO;
    }

    @Override
    public byte[] serialize(Object obj) {
        if (obj == null) {
            return new byte[0];
        }

        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
             Output output = new Output(byteArrayOutputStream)) {

            // 获取当前线程的Kryo实例
            Kryo kryo = kryoThreadLocal.get();

            // 序列化对象
            kryo.writeObject(output, obj);
            output.flush();

            return byteArrayOutputStream.toByteArray();

        } catch (Exception e) {
            log.error("Kryo序列化失败，对象类型：{}", obj.getClass().getName(), e);
            throw new RuntimeException("Kryo序列化失败", e);
        }
    }

    @Override
    public <T> T deserialize(byte[] bytes, Class<T> clazz) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }

        try (ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bytes);
             Input input = new Input(byteArrayInputStream)) {

            // 获取当前线程的Kryo实例
            Kryo kryo = kryoThreadLocal.get();

            // 反序列化
            return kryo.readObject(input, clazz);

        } catch (Exception e) {
            log.error("Kryo反序列化失败，目标类型：{}", clazz.getName(), e);
            throw new RuntimeException("Kryo反序列化失败", e);
        }
    }

    /**
     * 清理ThreadLocal，防止内存泄漏
     * 可以在请求结束时调用
     */
    public static void remove() {
        kryoThreadLocal.remove();
    }
}