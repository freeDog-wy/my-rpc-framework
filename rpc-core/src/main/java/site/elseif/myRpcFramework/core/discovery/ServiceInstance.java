package site.elseif.myRpcFramework.core.discovery;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 服务实例，包含一个服务提供者的基本信息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ServiceInstance {
    private String serviceName;  // 服务名称
    private String ip;           // IP地址
    private int port;            // 端口号

    // 可以添加权重、健康状态等字段，但先保持简单
}