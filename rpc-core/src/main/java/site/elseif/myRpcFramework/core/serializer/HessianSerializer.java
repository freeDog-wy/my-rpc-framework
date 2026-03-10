package site.elseif.myRpcFramework.core.serializer;

import com.caucho.hessian.io.Hessian2Input;
import com.caucho.hessian.io.Hessian2Output;
import com.caucho.hessian.io.SerializerFactory;
import site.elseif.myRpcFramework.common.MessageConstants;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import lombok.extern.slf4j.Slf4j;
/**
 * Hessian序列化实现
 * 优点：跨语言、使用简单、性能良好
 * 缺点：需要对象实现Serializable接口
 */
@Slf4j
public class HessianSerializer implements Serializer {

    /**
     * Hessian的SerializerFactory可以复用，是线程安全的
     */
    private static final SerializerFactory SERIALIZER_FACTORY = new SerializerFactory();

    @Override
    public byte getSerializerType() {
        return MessageConstants.SERIALIZER_HESSIAN;  // 假设常量值为2
    }

    @Override
    public byte[] serialize(Object obj) {
        if (obj == null) {
            return new byte[0];
        }

        // 注意：Hessian要求序列化的对象必须实现java.io.Serializable接口
        if (!(obj instanceof java.io.Serializable)) {
            log.warn("对象未实现Serializable接口，Hessian序列化可能失败：{}", obj.getClass().getName());
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            // Hessian2Output 使用 Hessian 2.0 协议（性能更好，更紧凑）
            Hessian2Output output = new Hessian2Output(baos);
            output.setSerializerFactory(SERIALIZER_FACTORY);

            // 开始序列化
            output.writeObject(obj);
            output.flush();  // 重要：必须flush，确保所有数据写入流

            byte[] bytes = baos.toByteArray();

            if (log.isDebugEnabled()) {
                log.debug("Hessian序列化成功，类型：{}，大小：{}字节",
                        obj.getClass().getSimpleName(), bytes.length);
            }

            return bytes;

        } catch (IOException e) {
            log.error("Hessian序列化失败，对象类型：{}", obj.getClass().getName(), e);
            throw new RuntimeException("Hessian序列化失败", e);
        }
    }

    @Override
    public <T> T deserialize(byte[] bytes, Class<T> clazz) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }

        try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes)) {
            Hessian2Input input = new Hessian2Input(bais);
            input.setSerializerFactory(SERIALIZER_FACTORY);

            // 反序列化（返回Object类型）
            Object obj = input.readObject();

            if (log.isDebugEnabled()) {
                log.debug("Hessian反序列化成功，目标类型：{}，实际类型：{}",
                        clazz.getSimpleName(), obj.getClass().getSimpleName());
            }

            return clazz.cast(obj);

        } catch (IOException e) {
            log.error("Hessian反序列化失败，目标类型：{}", clazz.getName(), e);
            throw new RuntimeException("Hessian反序列化失败", e);
        }
    }

    /**
     * 带类型检查的反序列化（可选）
     */
    @SuppressWarnings("unchecked")
    public <T> T deserialize(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }

        try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes)) {
            Hessian2Input input = new Hessian2Input(bais);
            input.setSerializerFactory(SERIALIZER_FACTORY);
            return (T) input.readObject();
        } catch (IOException e) {
            log.error("Hessian反序列化失败", e);
            throw new RuntimeException("Hessian反序列化失败", e);
        }
    }
}