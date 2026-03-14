package site.elseif.myRpcFramework.core.discovery;

import site.elseif.myRpcFramework.core.loadBalance.LoadBalancer;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;
import site.elseif.myRpcFramework.core.serializer.KryoSerializer;
import site.elseif.myRpcFramework.core.serializer.Serializer;

/**
 * 支持磁盘持久化的服务发现装饰器
 * 三层防护机制：
 * 1. 内存缓存（实时更新）
 * 2. 注册中心获取（缓存过期时）
 * 3. 磁盘持久化（注册中心不可用时）
 */
@Slf4j
public class PersistentServiceDiscovery implements ServiceDiscovery {

    private final ServiceDiscovery delegate;  // 实际的服务发现（NacosServiceDiscovery）
    private final Map<String, CacheEntry> memoryCache = new ConcurrentHashMap<>();
    private final String persistentDir;  // 磁盘持久化目录
    private final long cacheExpireTimeMs;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final Serializer serializer = new KryoSerializer();  // 使用Kryo序列化器

    public PersistentServiceDiscovery(ServiceDiscovery delegate) {
        this(delegate, 60 * 1000, "service_cache");
    }

    public PersistentServiceDiscovery(ServiceDiscovery delegate, long cacheExpireTimeMs, String persistentDir) {
        this.delegate = delegate;
        this.cacheExpireTimeMs = cacheExpireTimeMs;
        this.persistentDir = persistentDir;

        // 创建持久化目录
        createPersistentDir();

        // 启动定时持久化任务
        startPersistentTask();

        // 应用启动时加载磁盘缓存
        loadFromDisk();
    }

    @Override
    public ServiceInstance discover(String serviceName, LoadBalancer loadBalancer) {
        // 第一层防护：从内存缓存获取
        CacheEntry memoryEntry = memoryCache.get(serviceName);

        if (memoryEntry != null && !memoryEntry.isExpired(cacheExpireTimeMs)) {
            List<ServiceInstance> instances = memoryEntry.getInstances();
            if (!instances.isEmpty()) {
                ServiceInstance selected = loadBalancer.select(instances);
                log.debug("内存缓存命中：{} -> {}:{}", serviceName, selected.getIp(), selected.getPort());
                return selected;
            }
        }

        // 第二层防护：从注册中心获取
        try {
            List<ServiceInstance> instances = getAllInstances(serviceName);
            if (!instances.isEmpty()) {
                // 获取成功，更新内存和磁盘缓存
                updateCache(serviceName, instances);
                return loadBalancer.select(instances);
            }
        } catch (Exception e) {
            log.warn("从注册中心获取服务实例失败：{}", serviceName, e);
        }

        // 第三层防护：从磁盘缓存获取
        List<ServiceInstance> diskInstances = loadFromDisk(serviceName);
        if (!diskInstances.isEmpty()) {
            log.info("磁盘缓存命中：{}，共{}个实例", serviceName, diskInstances.size());
            // 更新内存缓存（使用较短的过期时间，避免长期使用过期数据）
            CacheEntry diskEntry = new CacheEntry(diskInstances);
            memoryCache.put(serviceName, diskEntry);
            return loadBalancer.select(diskInstances);
        }

        // 所有防护都失效，返回内存中可能存在的过期数据
        if (memoryEntry != null && !memoryEntry.getInstances().isEmpty()) {
            log.warn("所有缓存均失效，返回内存过期数据：{}", serviceName);
            return loadBalancer.select(memoryEntry.getInstances());
        }

        throw new RuntimeException("无法获取服务实例：" + serviceName);
    }

    @Override
    public List<ServiceInstance> getAllInstances(String serviceName) {
        try {
            List<ServiceInstance> instances = delegate.getAllInstances(serviceName);
            if (!instances.isEmpty()) {
                // 更新缓存
                updateCache(serviceName, instances);
            }
            return instances;
        } catch (Exception e) {
            log.error("从注册中心获取服务实例失败：{}", serviceName, e);
            // 返回磁盘缓存数据
            return loadFromDisk(serviceName);
        }
    }

    /**
     * 更新内存和磁盘缓存
     */
    private void updateCache(String serviceName, List<ServiceInstance> instances) {
        // 更新内存缓存
        CacheEntry memoryEntry = new CacheEntry(instances);
        memoryCache.put(serviceName, memoryEntry);

        // 异步持久化到磁盘
        persistToDiskAsync(serviceName, instances);
    }

    /**
     * 异步持久化到磁盘
     */
    private void persistToDiskAsync(String serviceName, List<ServiceInstance> instances) {
        scheduler.submit(() -> {
            try {
                persistToDisk(serviceName, instances);
            } catch (Exception e) {
                log.error("持久化服务实例到磁盘失败：{}", serviceName, e);
            }
        });
    }

    /**
     * 使用Kryo持久化到磁盘
     */
    private void persistToDisk(String serviceName, List<ServiceInstance> instances) {
        String fileName = getCacheFileName(serviceName);
        String tempFileName = fileName + ".tmp";
        Path filePath = Paths.get(persistentDir, fileName);
        Path tempFilePath = Paths.get(persistentDir, tempFileName);

        try {
            // 先序列化数据
            byte[] data = serializer.serialize(instances);

            // 写入临时文件
            try (FileOutputStream fos = new FileOutputStream(tempFilePath.toFile())) {
                fos.write(data);
                fos.flush();
            }

            // 确保数据写入磁盘
            Files.getFileStore(tempFilePath).isReadOnly();

            // 原子操作：重命名临时文件为目标文件
            Files.move(tempFilePath, filePath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);

            log.debug("服务实例持久化成功：{}，实例数量：{}", serviceName, instances.size());
        } catch (IOException e) {
            log.error("写入磁盘缓存失败：{}", serviceName, e);
            // 清理临时文件
            try {
                Files.deleteIfExists(tempFilePath);
            } catch (IOException ex) {
                log.warn("清理临时文件失败：{}", tempFileName);
            }
        }
    }

    /**
     * 使用Kryo从磁盘加载指定服务的实例
     */
    @SuppressWarnings("unchecked")
    private List<ServiceInstance> loadFromDisk(String serviceName) {
        String fileName = getCacheFileName(serviceName);
        Path filePath = Paths.get(persistentDir, fileName);

        if (!Files.exists(filePath)) {
            return List.of();
        }

        try {
            // 读取文件数据
            byte[] data = Files.readAllBytes(filePath);

            // 反序列化
            if (data.length > 0) {
                List<ServiceInstance> instances = serializer.deserialize(data, List.class);
                log.debug("从磁盘加载服务实例成功：{}，实例数量：{}", serviceName, instances.size());
                return instances;
            }
        } catch (IOException e) {
            log.error("读取磁盘缓存文件失败：{}", serviceName, e);
        } catch (Exception e) {
            log.error("反序列化磁盘缓存失败：{}", serviceName, e);
            // 如果文件损坏，删除它
            try {
                Files.deleteIfExists(filePath);
                log.info("已删除损坏的缓存文件：{}", fileName);
            } catch (IOException ex) {
                log.warn("删除损坏的缓存文件失败：{}", fileName);
            }
        }

        return List.of();
    }

    /**
     * 应用启动时加载所有磁盘缓存
     */
    private void loadFromDisk() {
        File dir = new File(persistentDir);
        File[] files = dir.listFiles((d, name) -> name.endsWith(".cache"));

        if (files != null) {
            for (File file : files) {
                String serviceName = file.getName().replace(".cache", "");
                List<ServiceInstance> instances = loadFromDisk(serviceName);
                if (!instances.isEmpty()) {
                    // 使用加载时间作为时间戳，但设置较短的过期时间
                    CacheEntry diskEntry = new CacheEntry(instances);
                    memoryCache.put(serviceName, diskEntry);
                    log.info("从磁盘加载服务实例：{}，共{}个实例", serviceName, instances.size());
                }
            }
        }
    }

    /**
     * 创建持久化目录
     */
    private void createPersistentDir() {
        File dir = new File(persistentDir);
        if (!dir.exists()) {
            if (dir.mkdirs()) {
                log.info("创建持久化目录：{}", persistentDir);
            } else {
                log.error("创建持久化目录失败：{}", persistentDir);
            }
        }
    }

    /**
     * 启动定时持久化任务
     */
    private void startPersistentTask() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                // 定期将内存缓存持久化到磁盘
                memoryCache.forEach((serviceName, entry) -> {
                    if (!entry.getInstances().isEmpty()) {
                        persistToDiskAsync(serviceName, entry.getInstances());
                    }
                });
            } catch (Exception e) {
                log.error("定时持久化任务执行失败", e);
            }
        }, 5, 30, TimeUnit.MINUTES);  // 每30分钟持久化一次
    }

    /**
     * 获取缓存文件名
     */
    private String getCacheFileName(String serviceName) {
        return serviceName.replace('/', '_').replace(':', '_') + ".cache";
    }

    @Override
    public void close() {
        // 关闭前持久化所有缓存
        memoryCache.forEach((serviceName, entry) -> {
            persistToDisk(serviceName, entry.getInstances());
        });

        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        delegate.close();
    }
}
