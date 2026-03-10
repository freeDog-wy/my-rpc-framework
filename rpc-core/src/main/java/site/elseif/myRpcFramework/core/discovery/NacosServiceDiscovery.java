package site.elseif.myRpcFramework.core.discovery;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.runtime.NacosLoadException;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import site.elseif.myRpcFramework.core.loadBalance.LoadBalancer;

import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * 基于 Nacos 的服务发现实现
 * 替代 discovery.ManualServiceDiscovery
 */
@Slf4j
public class NacosServiceDiscovery implements ServiceDiscovery {

    /**
     * -- GETTER --
     *  获取原始的 Nacos NamingService（如果需要更复杂的操作）
     */
    @Getter
    private final NamingService namingService;
    private final String serverAddr;

    /**
     * 构造函数
     * @param serverAddr Nacos 服务器地址，如 "localhost:8848"
     */
    public NacosServiceDiscovery(String serverAddr, String namespace) {
        this.serverAddr = serverAddr;
        try {
            // 创建 Nacos NamingService 实例
            Properties properties = new Properties();
            properties.setProperty("serverAddr", serverAddr);

            // 可以添加更多配置，如命名空间、认证等
             properties.setProperty("namespace", namespace);

            this.namingService = NacosFactory.createNamingService(properties);
            log.info("Nacos 服务发现初始化成功，连接地址：{}", serverAddr);
        } catch (NacosException e) {
            log.error("Nacos 服务发现初始化失败", e);
            throw new RuntimeException("Nacos 初始化失败", e);
        }
    }

    @Override
    public List<ServiceInstance> getAllInstances(String serviceName) {
        try {
            // 1. 从 Nacos 获取所有健康的服务实例
            List<Instance> instances = namingService.selectInstances(serviceName, true);
            return instances.stream()
                    .map(instance -> new ServiceInstance(
                            serviceName,
                            instance.getIp(),
                            instance.getPort()
                    ))
                    .collect(Collectors.toList());
        } catch (NacosException e) {
            log.error("获取服务实例列表失败，serviceName: {}", serviceName, e);
            return List.of();  // 返回空列表，调用方会处理没有实例的情况
        }
    }

    @Override
    public void close() {

    }

    @Override
    public ServiceInstance discover(String serviceName, LoadBalancer loadBalancer) {
        // 1. 从 Nacos 获取所有健康的服务实例
        List<ServiceInstance> serviceInstances = getAllInstances(serviceName);
        // 2. 使用负载均衡选择一个实例
        ServiceInstance selected = loadBalancer.select(serviceInstances);

        log.info("服务发现：{} -> {}:{}", serviceName, selected.getIp(), selected.getPort());
        return selected;
    }

}