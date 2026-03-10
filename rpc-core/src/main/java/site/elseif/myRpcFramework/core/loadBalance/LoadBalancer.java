package site.elseif.myRpcFramework.core.loadBalance;

import site.elseif.myRpcFramework.core.discovery.ServiceInstance;

import java.util.List;

/**
 * 负载均衡接口
 */
public interface LoadBalancer {

    /**
     * 从服务实例列表中选择一个
     */
    ServiceInstance select(List<ServiceInstance> instances);
}