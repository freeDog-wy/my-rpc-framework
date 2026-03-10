package site.elseif.myRpcFramework.core.discovery;

import site.elseif.myRpcFramework.core.loadBalance.LoadBalancer;

import java.util.List;

public interface ServiceDiscovery {

    /**
     * 根据服务名发现一个服务实例
     * @param serviceName 服务名称（接口全限定名）
     * @param loadBalancer 负载均衡策略
     * @return 服务实例
     */
    ServiceInstance discover(String serviceName, LoadBalancer loadBalancer);

    /**
     * 获取某个服务的所有实例
     * @param serviceName 服务名称（接口全限定名）
     * @return 服务实例列表
     */
    List<ServiceInstance> getAllInstances(String serviceName);

    /**
     * 关闭服务发现，释放资源
     */
    void close();
}