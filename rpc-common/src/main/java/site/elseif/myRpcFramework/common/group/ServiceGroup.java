package site.elseif.myRpcFramework.common.group;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务分组配置
 */
@Slf4j
public class ServiceGroup {

    // 分组定义
    @AllArgsConstructor
    @Getter
    public enum Group {
        CORE(0, "核心业务", 100),      // 权重100
        BUSINESS(1, "普通业务", 50),    // 权重50
        AUXILIARY(2, "辅助业务", 20);   // 权重20

        private final int code;
        private final String desc;
        private final int weight;  // 线程池权重
    }

    // 服务分组映射
    private final Map<String, Group> serviceGroupMap = new ConcurrentHashMap<>();

    /**
     * 配置服务分组
     */
    public void setGroup(String serviceName, Group group) {
        serviceGroupMap.put(serviceName, group);
        log.info("应用服务分组配置：{} -> {}", serviceName, group.getDesc());
    }

    /**
     * 获取服务分组
     */
    public Group getGroup(String serviceName) {
        return serviceGroupMap.getOrDefault(serviceName, Group.BUSINESS);
    }
}