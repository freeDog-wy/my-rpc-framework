package site.elseif.myRpcFramework.core.server;

import lombok.extern.slf4j.Slf4j;
import site.elseif.myRpcFramework.common.group.ServiceGroup;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 分组线程池 - 实现流量隔离
 */
@Slf4j
public class GroupedThreadPool {

    // 不同分组使用不同的线程池
    private final Map<ServiceGroup.Group, ThreadPoolExecutor> groupPools = new ConcurrentHashMap<>();

    public GroupedThreadPool() {
        // 核心业务线程池
        groupPools.put(ServiceGroup.Group.CORE, createPool(
                20, 50, 1000, "core-group"));

        // 普通业务线程池
        groupPools.put(ServiceGroup.Group.BUSINESS, createPool(
                10, 30, 500, "business-group"));

        // 辅助业务线程池
        groupPools.put(ServiceGroup.Group.AUXILIARY, createPool(
                5, 15, 200, "auxiliary-group"));
    }

    private ThreadPoolExecutor createPool(int coreSize, int maxSize, int queueSize, String name) {
        return new ThreadPoolExecutor(
                coreSize,
                maxSize,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queueSize),
                new ThreadFactory() {
                    private final AtomicInteger threadId = new AtomicInteger(1);
                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(r, name + "-" + threadId.getAndIncrement());
                        t.setDaemon(true);
                        return t;
                    }
                },
                new ThreadPoolExecutor.CallerRunsPolicy()  // 拒绝时在调用线程执行
        );
    }

    /**
     * 提交任务到对应分组的线程池
     */
    public Future<?> submit(ServiceGroup.Group group, Runnable task) {
        ThreadPoolExecutor executor = groupPools.get(group);
        if (executor == null) {
            executor = groupPools.get(ServiceGroup.Group.BUSINESS);
        }

        return executor.submit(task);
    }

    /**
     * 获取线程池状态
     */
    public void printPoolStats() {
        groupPools.forEach((group, pool) -> {
            log.info("{}线程池 - 活跃: {}, 队列: {}, 已完成: {}",
                    group.getDesc(),
                    pool.getActiveCount(),
                    pool.getQueue().size(),
                    pool.getCompletedTaskCount()
            );
        });
    }
}