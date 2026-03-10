package site.elseif.myRpcFramework.core.registry;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import lombok.extern.slf4j.Slf4j;

import java.util.Properties;

/**
 * 基于 Nacos 的服务注册
 * 服务提供者使用此类将自己的信息注册到 Nacos
 */
@Slf4j
public class NacosServiceRegistry implements ServiceRegistry {

    private final NamingService namingService;
    private final String serverAddr;

    public NacosServiceRegistry(String serverAddr, String namespace) {
        this.serverAddr = serverAddr;
        try {
            Properties properties = new Properties();
            properties.setProperty("serverAddr", serverAddr);
            properties.setProperty("namespace", namespace); // 可以根据需要设置命名空间
            this.namingService = NacosFactory.createNamingService(properties);
            log.info("Nacos 服务注册初始化成功，连接地址：{}", serverAddr);
        } catch (NacosException e) {
            log.error("Nacos 服务注册初始化失败", e);
            throw new RuntimeException("Nacos 初始化失败", e);
        }
    }

    /**
     * 注册服务
     * @param serviceName 服务名（接口全限定名）
     * @param ip 服务IP
     * @param port 服务端口
     */
    @Override
    public void registerService(String serviceName, String ip, int port) {
        try {
            namingService.registerInstance(serviceName, ip, port);
            log.info("服务注册成功：{} -> {}:{}", serviceName, ip, port);
        } catch (NacosException e) {
            log.error("服务注册失败，serviceName: {}", serviceName, e);
            throw new RuntimeException("服务注册失败", e);
        }
    }

    /**
     * 注销服务
     */
    @Override
    public void deregisterService(String serviceName, String ip, int port) {
        try {
            namingService.deregisterInstance(serviceName, ip, port);
            log.info("服务注销成功：{} -> {}:{}", serviceName, ip, port);
        } catch (NacosException e) {
            log.error("服务注销失败", e);
        }
    }
}