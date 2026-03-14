package site.elseif.myRpcFramework.core.discovery;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 缓存条目
 */
public class CacheEntry {
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
