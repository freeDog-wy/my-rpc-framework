package site.elseif.myRpcFramework.core.serializer;

import lombok.extern.slf4j.Slf4j;
import site.elseif.myRpcFramework.common.MessageConstants;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Enumeration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于SPI的序列化工厂
 * 支持动态加载和切换序列化实现
 */
@Slf4j
public class SerializerFactory {

    // 序列化类型与实现类的映射
    private static final Map<Byte, Serializer> SERIALIZER_MAP = new ConcurrentHashMap<>();

    // 序列化类型与SPI中配置的类名的映射（用于动态加载）
    private static final Map<Byte, String> SERIALIZER_CLASS_NAMES = new ConcurrentHashMap<>();

    static {
        // 初始化类型-类名映射（这些常量定义在MessageConstants中）
        SERIALIZER_CLASS_NAMES.put(MessageConstants.SERIALIZER_KRYO,
                "site.elseif.rpc.core.serializer.site.elseif.myRpcFramwork.core.serializer.KryoSerializer");
        SERIALIZER_CLASS_NAMES.put(MessageConstants.SERIALIZER_PROTOBUF,
                "site.elseif.rpc.core.serializer.ProtobufSerializer");
        SERIALIZER_CLASS_NAMES.put(MessageConstants.SERIALIZER_HESSIAN,
                "site.elseif.rpc.core.serializer.site.elseif.myRpcFramwork.core.serializer.HessianSerializer");

        // 通过SPI加载所有序列化实现
        loadSerializersBySPI();
    }

    /**
     * 通过SPI机制加载序列化实现
     */
    private static void loadSerializersBySPI() {
        try {
            // 使用ServiceLoader方式（Java标准SPI）
            // java.util.ServiceLoader<site.elseif.myRpcFramwork.core.serializer.Serializer> serviceLoader =
            //     ServiceLoader.load(site.elseif.myRpcFramwork.core.serializer.Serializer.class);
            // for (site.elseif.myRpcFramwork.core.serializer.Serializer serializer : serviceLoader) {
            //     registerSerializer(serializer);
            // }

            // 或者手动读取配置文件（更灵活，可以自定义类型）
            loadFromConfigFile();

        } catch (Exception e) {
            log.error("SPI加载序列化器失败", e);
        }
    }

    /**
     * 手动读取SPI配置文件（方式一）
     */
    private static void loadFromConfigFile() {
        try {
            String resourceName = "META-INF/services/" + Serializer.class.getName();
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            Enumeration<URL> urls = classLoader.getResources(resourceName);

            while (urls.hasMoreElements()) {
                URL url = urls.nextElement();
                log.info("找到SPI配置文件：{}", url);

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(url.openStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (line.isEmpty() || line.startsWith("#")) {
                            continue;
                        }

                        // 加载并实例化
                        Class<?> clazz = Class.forName(line);
                        if (Serializer.class.isAssignableFrom(clazz)) {
                            Serializer serializer = (Serializer) clazz.getDeclaredConstructor().newInstance();
                            registerSerializer(serializer);
                            log.info("SPI加载序列化器：{} -> {}",
                                    serializer.getSerializerType(), line);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("读取SPI配置文件失败", e);
        }
    }

    /**
     * 注册序列化器
     */
    private static void registerSerializer(Serializer serializer) {
        byte type = serializer.getSerializerType();
        if (SERIALIZER_MAP.containsKey(type)) {
            log.warn("序列化类型 {} 已存在，将被覆盖", type);
        }
        SERIALIZER_MAP.put(type, serializer);
    }

    /**
     * 获取序列化器
     */
    public static Serializer getSerializer(byte type) {
        Serializer serializer = SERIALIZER_MAP.get(type);

        // 如果还没加载，尝试动态加载
        if (serializer == null) {
            serializer = loadSerializer(type);
        }

        if (serializer == null) {
            throw new RuntimeException("不支持的序列化类型：" + type);
        }

        return serializer;
    }

    /**
     * 动态加载指定的序列化器（懒加载）
     */
    private static synchronized Serializer loadSerializer(byte type) {
        // 双重检查
        Serializer serializer = SERIALIZER_MAP.get(type);
        if (serializer != null) {
            return serializer;
        }

        String className = SERIALIZER_CLASS_NAMES.get(type);
        if (className == null) {
            return null;
        }

        try {
            Class<?> clazz = Class.forName(className);
            serializer = (Serializer) clazz.getDeclaredConstructor().newInstance();
            SERIALIZER_MAP.put(type, serializer);
            log.info("动态加载序列化器：{}", className);
            return serializer;
        } catch (Exception e) {
            log.error("加载序列化器失败：{}", className, e);
            return null;
        }
    }

    /**
     * 获取所有可用的序列化类型
     */
    public static Map<Byte, String> getAvailableSerializers() {
        Map<Byte, String> available = new ConcurrentHashMap<>();
        for (Map.Entry<Byte, String> entry : SERIALIZER_CLASS_NAMES.entrySet()) {
            if (SERIALIZER_MAP.containsKey(entry.getKey())) {
                available.put(entry.getKey(), entry.getValue());
            }
        }
        return available;
    }
}