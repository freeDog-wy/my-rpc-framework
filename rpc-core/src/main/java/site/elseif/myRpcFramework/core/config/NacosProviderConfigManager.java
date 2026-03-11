package site.elseif.myRpcFramework.core.config;

import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.Yaml;
import site.elseif.myRpcFramework.common.group.ServiceGroup;

import java.util.List;
import java.util.Map;

@Slf4j
public class NacosProviderConfigManager extends NacosConfigManager {
    public NacosProviderConfigManager(String serverAddr, String dataId, String group, ServiceConfigManager serviceConfigManager) {
        super(serverAddr, dataId, group, serviceConfigManager);
    }

    @Override
    public void GetAndParseConfig(String yamlConfig) {
        try {
            Yaml yaml = new Yaml();
            Map<String, Object> config = yaml.load(yamlConfig);

            // 解析服务列表
            List<Map<String, Object>> services = (List<Map<String, Object>>) config.get("services");
            if (services == null) return;

            for (Map<String, Object> serviceConfig : services) {
                String serviceName = (String) serviceConfig.get("serviceName");
                Map<String, Object> providerConfig = (Map<String, Object>) serviceConfig.get("provider");
                String group = (String) providerConfig.get("group");

                // 设置服务分组
                if (group != null) {
                    serviceConfigManager.setGroup(serviceName,
                            ServiceGroup.Group.valueOf(group));
                }

                // 解析方法配置
                List<Map<String, Object>> methods = (List<Map<String, Object>>) providerConfig.get("methods");
                if (methods != null) {
                    for (Map<String, Object> methodConfig : methods) {
                        String methodName = (String) methodConfig.get("name");
                        Object qpsObj = methodConfig.get("flowLimitQps");
                        if (qpsObj instanceof Number) {
                            long qps = ((Number) qpsObj).longValue();
                            serviceConfigManager.limitMethod(serviceName, methodName, qps);
                        }

                        Map<String, Object> cb = (Map<String, Object>) methodConfig.get("circuitBreaker");
                        if (cb != null) {
                            Object ftObj = cb.get("failureThreshold");
                            Integer failureThreshold = (ftObj instanceof Number) ? ((Number) ftObj).intValue() : null;

                            Object tmObj = cb.get("timeoutMs");
                            Integer timeoutMs = (tmObj instanceof Number) ? ((Number) tmObj).intValue() : null;

                            Object hsObj = cb.get("halfOpenSuccess");
                            Integer halfOpenSuccess = (hsObj instanceof Number) ? ((Number) hsObj).intValue() : null;

                            serviceConfigManager.circuitBreakMethod(serviceName, methodName,
                                    failureThreshold, timeoutMs, halfOpenSuccess);
                        }
                    }
                }

                // 处理默认配置
                Map<String, Object> defaultConfig = (Map<String, Object>) serviceConfig.get("defaultMethodConfig");
                if (defaultConfig != null) {
                    // 设置默认配置...
                }
            }

            log.info("配置应用完成");

        } catch (Exception e) {
            log.error("解析配置失败", e);
        }
    }
}
