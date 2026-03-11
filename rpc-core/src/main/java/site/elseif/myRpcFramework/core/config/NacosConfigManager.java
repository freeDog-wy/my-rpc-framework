package site.elseif.myRpcFramework.core.config;


import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.Yaml;
import site.elseif.myRpcFramework.common.group.ServiceGroup;

import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Nacos配置中心管理器
 * 负责拉取和监听治理规则配置
 */
@Slf4j
public abstract class NacosConfigManager {

    private final ConfigService configService;
    private final String dataId;
    private final String group;

    // 配置变更监听器
    protected final ServiceConfigManager serviceConfigManager;

    public NacosConfigManager(String serverAddr, String dataId, String group, ServiceConfigManager serviceConfigManager) {
        this.dataId = dataId;
        this.group = group;
        this.serviceConfigManager = serviceConfigManager;
        try {
            Properties properties = new Properties();
            properties.setProperty("serverAddr", serverAddr);
            this.configService = NacosFactory.createConfigService(properties);
            log.info("Nacos配置中心初始化成功：{}", serverAddr);

            // 启动时拉取初始配置
            loadInitialConfig();

            // 添加监听器，实现动态更新
            addConfigListener();

        } catch (NacosException e) {
            log.error("Nacos配置中心初始化失败", e);
            throw new RuntimeException("Nacos配置中心初始化失败", e);
        }
    }

    /**
     * 加载初始配置
     */
    private void loadInitialConfig() {
        try {
            String config = configService.getConfig(dataId, group, 5000);
            if (config != null && !config.isEmpty()) {
                GetAndParseConfig(config);
                log.info("加载初始配置成功");
            } else {
                log.warn("未找到配置，使用默认配置");
            }
        } catch (NacosException e) {
            log.error("加载初始配置失败", e);
        }
    }

    /**
     * 添加配置监听器
     * Nacos通过长轮询实现实时推送
     */
    private void addConfigListener() {
        try {
            configService.addListener(dataId, group, new Listener() {
                @Override
                public Executor getExecutor() {
                    return Executors.newSingleThreadExecutor(); // 异步执行
                }

                @Override
                public void receiveConfigInfo(String configInfo) {
                    log.info("检测到配置变更，重新加载...");
                    GetAndParseConfig(configInfo);
                    serviceConfigManager.applyConfigs();
                }
            });
            log.info("添加配置监听器成功");
        } catch (NacosException e) {
            log.error("添加配置监听器失败", e);
        }
    }

    /**
     * 应用配置到治理组件
     * 使用YAML解析配置
     */
    protected abstract void GetAndParseConfig(String yamlConfig);
}