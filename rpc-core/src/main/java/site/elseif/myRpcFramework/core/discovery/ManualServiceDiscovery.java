package site.elseif.myRpcFramework.core.discovery;

import lombok.extern.slf4j.Slf4j;
import site.elseif.myRpcFramework.core.loadBalance.LoadBalancer;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class ManualServiceDiscovery implements ServiceDiscovery {

    /**
     * 手动配置的服务列表
     * key: 服务接口名
     * value: 该服务的所有实例列表
     */
    private final Map<String, List<ServiceInstance>> serviceMap = new ConcurrentHashMap<>();

    public ManualServiceDiscovery() {
        // 初始化写死的服务配置
        initServices();
    }

    private void initServices() {
        // 写死 HelloService 的提供者地址
        // 假设你的服务提供者在本地8080端口启动
        List<ServiceInstance> helloServiceInstances = Arrays.asList(
                new ServiceInstance(
                        "HelloService",
                        "127.0.0.1",
                        8080
                )
                // 可以添加多个实例测试负载均衡
                // new site.elseif.myRpcFramwork.core.discovery.ServiceInstance("site.elseif.rpc.api.HelloService", "127.0.0.1", 8081)
        );

        serviceMap.put("HelloService", helloServiceInstances);

        // 如果有其他服务，继续添加
        // serviceMap.put("site.elseif.rpc.api.UserService", userServiceInstances);

        log.info("手动服务发现初始化完成，已配置的服务：{}", serviceMap.keySet());
    }

    @Override
    public ServiceInstance discover(String serviceName, LoadBalancer loadBalancer) {
        // 1. 获取服务列表
        List<ServiceInstance> instances = serviceMap.get(serviceName);

        if (instances == null || instances.isEmpty()) {
            throw new RuntimeException("未找到服务：" + serviceName);
        }

        // 2. 使用负载均衡选择一个实例
        ServiceInstance instance = loadBalancer.select(instances);

        log.info("服务发现：{} -> {}:{}", serviceName, instance.getIp(), instance.getPort());

        return instance;
    }

    @Override
    public List<ServiceInstance> getAllInstances(String serviceName) {
        List<ServiceInstance> instances = serviceMap.get(serviceName);
        if (instances == null) {
            throw new RuntimeException("未找到服务：" + serviceName);
        }
        return instances;
    }

    @Override
    public void close() {

    }

    /**
     * 动态添加服务（方便测试时添加）
     */
    public void registerService(String serviceName, String ip, int port) {
        ServiceInstance instance = new ServiceInstance(serviceName, ip, port);
        serviceMap.computeIfAbsent(serviceName, k -> new java.util.ArrayList<>())
                .add(instance);
        log.info("手动注册服务：{} -> {}:{}", serviceName, ip, port);
    }
}