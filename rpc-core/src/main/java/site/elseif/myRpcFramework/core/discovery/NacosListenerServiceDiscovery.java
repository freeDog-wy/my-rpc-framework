package site.elseif.myRpcFramework.core.discovery;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.listener.EventListener;
import com.alibaba.nacos.api.naming.listener.NamingEvent;
import com.alibaba.nacos.api.naming.pojo.Instance;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import site.elseif.myRpcFramework.core.loadBalance.LoadBalancer;

import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class NacosListenerServiceDiscovery implements ServiceDiscovery{

    /**
     * -- GETTER --
     *  获取原始的 Nacos NamingService（如果需要更复杂的操作）
     */
    @Getter
    private final NamingService namingService;

    private final Map<String, List<ServiceInstance>> subscribedServices; // 记录已订阅的服务列表

    private final EventListener eventListener;
    /**
     * 构造函数
     * @param serverAddr Nacos 服务器地址，如 "localhost:8848"
     */
    public NacosListenerServiceDiscovery(String serverAddr, String namespace) {
        subscribedServices = new ConcurrentHashMap<>();

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

        eventListener = event -> {
            // 这里可以根据事件类型进行不同的处理
            if (event instanceof NamingEvent) {
                String serviceName = ((NamingEvent) event).getServiceName();
                List<Instance> instances = ((NamingEvent) event).getInstances();
                log.info("Nacos 服务变更事件：{} -> {} instances", serviceName, instances.size());
                // 可以在这里更新本地缓存或触发其他逻辑
                List<ServiceInstance> serviceInstanceList = instances.stream()
                        .map(instance -> new ServiceInstance(
                                serviceName,
                                instance.getIp(),
                                instance.getPort()
                        ))
                        .toList();
                subscribedServices.put(serviceName, serviceInstanceList);
            }
        };
    }

    private void checkAndSubscribe(String serviceName) {
        if (!subscribedServices.containsKey(serviceName)) {
            try {
                namingService.subscribe(serviceName, eventListener);
                log.info("已订阅 Nacos 服务：{}", serviceName);
            } catch (NacosException e) {
                log.error("订阅 Nacos 服务失败，serviceName: {}", serviceName, e);
                throw new RuntimeException("订阅服务失败", e);
            }
        }
    }

    @Override
    public ServiceInstance discover(String serviceName, LoadBalancer loadBalancer) {
            // 1. 从已订阅的服务列表中获取实例
            List<ServiceInstance> instances = getAllInstances(serviceName);

            // 2. 使用负载均衡选择一个实例
            ServiceInstance selected = loadBalancer.select(instances);
            log.info("服务发现：{} -> {}:{}", serviceName, selected.getIp(), selected.getPort());
            return selected;
    }

    @Override
    public List<ServiceInstance> getAllInstances(String serviceName) {
        // 1. 先检查是否已经订阅过该服务
        checkAndSubscribe(serviceName);

        // 2. 从已订阅的服务列表中获取实例
        List<ServiceInstance> instances = subscribedServices.get(serviceName);
        if (instances == null || instances.isEmpty()) {
            log.warn("未找到服务实例：{}", serviceName);
            return List.of(); // 返回空列表，调用方会处理没有实例的情况
        }
        return instances;
    }

    @Override
    public void close() {
        for (String serviceName : subscribedServices.keySet()) {
            try {
                namingService.unsubscribe(serviceName, eventListener);
                log.info("已取消订阅 Nacos 服务：{}", serviceName);
            } catch (NacosException e) {
                log.error("取消订阅 Nacos 服务失败，serviceName: {}", serviceName, e);
            }
        }
    }
}
