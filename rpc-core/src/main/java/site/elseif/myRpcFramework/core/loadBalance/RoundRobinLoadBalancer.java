package site.elseif.myRpcFramework.core.loadBalance;

import site.elseif.myRpcFramework.core.discovery.ServiceInstance;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 平滑轮询负载均衡
 * <p>
 * 使用 AtomicInteger 保证多线程下计数器递增的原子性。
 * 通过取模运算轮流选择实例，保证请求在所有节点之间均匀分布。
 * </p>
 */
public class RoundRobinLoadBalancer implements LoadBalancer {

    /**
     * 全局请求计数器，使用 AtomicInteger 保证线程安全。
     * 每次 select 调用时递增，通过对实例数量取模确定目标节点。
     */
    private final AtomicInteger counter = new AtomicInteger(0);

    @Override
    public ServiceInstance select(List<ServiceInstance> instances) {
        if (instances == null || instances.isEmpty()) {
            return null;
        }

        // 如果只有一个实例，直接返回，无需计数
        if (instances.size() == 1) {
            return instances.getFirst();
        }

        int size = instances.size();

        // 使用 CAS 自增：为防止 counter 长期累积导致整数溢出，
        // 对负值取绝对值。counter 溢出为负数的概率极低，
        // 但加上这一行防御性代码更为健壮。
        int index = Math.abs(counter.getAndIncrement() % size);

        return instances.get(index);
    }
}
