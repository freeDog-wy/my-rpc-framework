package site.elseif.myRpcFramework.core.loadBalance;

import site.elseif.myRpcFramework.core.discovery.ServiceInstance;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 一致性哈希负载均衡
 * <p>
 * 核心思想：将所有节点映射到一个 [0, 2^32) 的虚拟哈希环上，
 * 请求根据自身哈希值顺时针找到最近的节点。
 * </p>
 *
 * <b>关键特性：</b>
 * <ul>
 *   <li><b>虚拟节点</b>：每个物理节点对应 {@code VIRTUAL_NODE_COUNT} 个虚拟节点，
 *       大幅减少数据倾斜问题（节点越少，倾斜越明显，虚拟节点数量建议 100~200）。</li>
 *   <li><b>哈希算法</b>：采用 MD5（取前4字节），分布比 String.hashCode() 更均匀，
 *       不受 JVM 实现差异影响，且 MD5 在此场景下不涉及安全性，性能可接受。</li>
 *   <li><b>环重建策略</b>：每次 select 时检测实例列表是否发生变更（基于实例签名），
 *       若变更则重建哈希环。使用 ConcurrentHashMap 存储签名，保证线程可见性。</li>
 * </ul>
 *
 * <b>调用者职责：</b>
 * 调用方需通过 {@link #selectWithKey(List, String)} 提供路由 key（如 userId、订单号等），
 * 以保证同一 key 的请求始终路由到同一节点（会话粘性）。
 * 若直接调用 {@link #select(List)}，则以当前线程 ID 作为 fallback key，不保证粘性。
 */
public class ConsistentHashLoadBalancer implements LoadBalancer {

    /**
     * 每个物理节点对应的虚拟节点数量。
     * 经验值：150 个虚拟节点在大多数场景下可将负载不均衡率控制在 5% 以内。
     */
    private static final int VIRTUAL_NODE_COUNT = 150;

    /**
     * 哈希环：key 是虚拟节点哈希值，value 是对应的物理服务实例。
     * 使用 TreeMap 支持 O(log n) 的 ceilingKey 查找（顺时针找最近节点）。
     */
    private volatile TreeMap<Long, ServiceInstance> hashRing = new TreeMap<>();

    /**
     * 上次构建哈希环时的实例列表签名，用于检测实例变更，避免重复重建哈希环。
     * key: 实例标识符（"ip:port"）
     */
    private final ConcurrentHashMap<String, Boolean> lastInstanceSignature = new ConcurrentHashMap<>();

    /**
     * 通过路由 key 选择服务实例（推荐使用此方法以实现会话粘性）。
     *
     * @param instances 当前可用的服务实例列表
     * @param routeKey  路由键，如 userId、sessionId、订单号等
     * @return 选中的服务实例
     */
    public ServiceInstance selectWithKey(List<ServiceInstance> instances, String routeKey) {
        if (instances == null || instances.isEmpty()) {
            return null;
        }
        if (instances.size() == 1) {
            return instances.getFirst();
        }

        // 检测实例列表是否变更，若变更则重建哈希环
        if (isInstancesChanged(instances)) {
            rebuildHashRing(instances);
        }

        // 计算请求的哈希值，顺时针找最近的虚拟节点
        long hash = md5Hash(routeKey);
        Map.Entry<Long, ServiceInstance> entry = hashRing.ceilingEntry(hash);

        // ceilingEntry 为 null 说明 hash 超过了环的最大值，取环上第一个节点（首尾相接）
        if (entry == null) {
            entry = hashRing.firstEntry();
        }

        return entry.getValue();
    }

    /**
     * 默认 select 方法，以线程 ID 作为路由 key（不保证会话粘性）。
     * 如果需要粘性路由，请改用 {@link #selectWithKey(List, String)}。
     */
    @Override
    public ServiceInstance select(List<ServiceInstance> instances) {
        // fallback：使用线程 ID 作为 key，分散不同线程的请求
        return selectWithKey(instances, String.valueOf(Thread.currentThread().threadId()));
    }

    /**
     * 重建哈希环。
     * 每个物理节点生成 {@link #VIRTUAL_NODE_COUNT} 个虚拟节点，
     * 虚拟节点 key 格式为 "ip:port#virtualIndex"。
     */
    private synchronized void rebuildHashRing(List<ServiceInstance> instances) {
        // double-check：防止多线程并发重建
        if (!isInstancesChanged(instances)) {
            return;
        }

        TreeMap<Long, ServiceInstance> newRing = new TreeMap<>();
        ConcurrentHashMap<String, Boolean> newSignature = new ConcurrentHashMap<>();

        for (ServiceInstance instance : instances) {
            String nodeKey = instance.getIp() + ":" + instance.getPort();
            newSignature.put(nodeKey, Boolean.TRUE);

            for (int i = 0; i < VIRTUAL_NODE_COUNT; i++) {
                // 虚拟节点唯一标识：在 ip:port 基础上追加虚拟索引
                String virtualNodeKey = nodeKey + "#" + i;
                long hash = md5Hash(virtualNodeKey);
                newRing.put(hash, instance);
            }
        }

        this.hashRing = newRing;
        this.lastInstanceSignature.clear();
        this.lastInstanceSignature.putAll(newSignature);
    }

    /**
     * 检测实例列表是否相较于上次构建时发生了变化。
     */
    private boolean isInstancesChanged(List<ServiceInstance> instances) {
        if (instances.size() != lastInstanceSignature.size()) {
            return true;
        }
        for (ServiceInstance instance : instances) {
            String nodeKey = instance.getIp() + ":" + instance.getPort();
            if (!lastInstanceSignature.containsKey(nodeKey)) {
                return true;
            }
        }
        return false;
    }

    /**
     * MD5 哈希，取结果的前 4 字节转为无符号 long。
     * 相比 String.hashCode()，MD5 分布更均匀，且与 JVM 无关。
     *
     * @param key 待哈希的字符串
     * @return [0, 2^32) 范围内的哈希值
     */
    private long md5Hash(String key) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(key.getBytes(StandardCharsets.UTF_8));
            // 取前 4 个字节组成 32 位无符号整数（用 long 存储避免符号位问题）
            return ((long) (digest[3] & 0xFF) << 24)
                    | ((long) (digest[2] & 0xFF) << 16)
                    | ((long) (digest[1] & 0xFF) << 8)
                    | ((long) (digest[0] & 0xFF));
        } catch (NoSuchAlgorithmException e) {
            // MD5 是 JDK 强制要求实现的算法，理论上不会抛出此异常
            throw new RuntimeException("MD5 algorithm not found", e);
        }
    }
}
