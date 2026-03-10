package site.elseif.myRpcFramework.core.loadBalance;

import site.elseif.myRpcFramework.core.discovery.ServiceInstance;

import java.util.List;
import java.util.Random;

/**
 * 随机负载均衡 - 先实现最简单的
 */
public class RandomLoadBalancer implements LoadBalancer {

    private final Random random = new Random();

    @Override
    public ServiceInstance select(List<ServiceInstance> instances) {
        if (instances == null || instances.isEmpty()) {
            return null;
        }

        // 如果只有一个实例，直接返回
        if (instances.size() == 1) {
            return instances.getFirst();
        }

        // 随机选择一个
        int index = random.nextInt(instances.size());
        return instances.get(index);
    }
}