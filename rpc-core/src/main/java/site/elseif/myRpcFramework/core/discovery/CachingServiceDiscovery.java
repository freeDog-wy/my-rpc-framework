package site.elseif.myRpcFramework.core.discovery;

import site.elseif.myRpcFramework.core.loadBalance.LoadBalancer;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import lombok.extern.slf4j.Slf4j;

/**
 * 支持本地缓存的服务发现装饰器
 */
@Slf4j
public class CachingServiceDiscovery implements ServiceDiscovery {

    private final ServiceDiscovery delegate;  // 实际的服务发现（如NacosServiceDiscovery）
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    // 缓存有效期（默认60秒）
    private final long cacheExpireTimeMs;

    public CachingServiceDiscovery(ServiceDiscovery delegate) {
        this(delegate, 60 * 1000);  // 默认60秒
    }

    public CachingServiceDiscovery(ServiceDiscovery delegate, long cacheExpireTimeMs) {
        this.delegate = delegate;
        this.cacheExpireTimeMs = cacheExpireTimeMs;
    }

    @Override
    public ServiceInstance discover(String serviceName, LoadBalancer loadBalancer) {
        // 1. 从缓存获取
        CacheEntry entry = cache.get(serviceName);

        // 2. 判断缓存是否有效
        if (entry != null && !entry.isExpired(cacheExpireTimeMs)) {
            List<ServiceInstance> instances = entry.getInstances();
            if (!instances.isEmpty()) {
                ServiceInstance selected = loadBalancer.select(instances);
                log.debug("缓存命中：{} -> {}:{}", serviceName, selected.getIp(), selected.getPort());
                return selected;
            }
        }

        // 3. 缓存失效或为空，重新获取
        log.info("缓存失效或为空，从注册中心获取：{}", serviceName);

        List<ServiceInstance> serviceInstances = getAllInstances(serviceName);
        if (serviceInstances.isEmpty() && entry != null && !entry.getInstances().isEmpty()) {
            // 如果注册中心访问失败但缓存有数据，返回缓存中的实例（可能是过期的）
            log.warn("注册中心访问失败，返回过期缓存：{} -> {}:{}", serviceName, entry.getInstances().getFirst().getIp(), entry.getInstances().getFirst().getPort());
            return loadBalancer.select(entry.getInstances());
        }
        return loadBalancer.select(serviceInstances);
    }

    /**
     * 获取所有服务实例并更新缓存
     * 需要扩展ServiceDiscovery接口支持
     */
    @Override
    public List<ServiceInstance> getAllInstances(String serviceName) {
        List<ServiceInstance> instances = delegate.getAllInstances(serviceName);
        log.info("从注册中心获取到 {} 个实例：{}", serviceName, instances.size());
        if (instances.isEmpty()) {
            return List.of();  // 返回空列表，调用方会处理没有实例的情况
        }
        cache.put(serviceName, new CacheEntry(instances));
        return instances;
    }

    @Override
    public void close() {
        delegate.close();
    }

    /**
     * 缓存条目
     */
    private static class CacheEntry {
        private final List<ServiceInstance> instances;
        private final long timestamp;

        CacheEntry(List<ServiceInstance> instances) {
            this.instances = new CopyOnWriteArrayList<>(instances);
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired(long cacheExpireTimeMs) {
            return System.currentTimeMillis() - timestamp > cacheExpireTimeMs;  // 60秒过期
        }

        List<ServiceInstance> getInstances() {
            return instances;
        }
    }
}