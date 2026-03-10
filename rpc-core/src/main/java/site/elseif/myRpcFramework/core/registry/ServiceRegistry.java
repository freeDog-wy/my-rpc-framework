package site.elseif.myRpcFramework.core.registry;

public interface ServiceRegistry {
    /**
     * 注册服务实例
     * @param serviceName 服务名称（接口全限定名）
     * @param host 服务实例所在主机地址
     * @param port 服务实例所在主机端口
     */
    void registerService(String serviceName, String host, int port);

    /**
     * 注销服务实例
     * @param serviceName 服务名称（接口全限定名）
     * @param host 服务实例所在主机地址
     * @param port 服务实例所在主机端口
     */
    void deregisterService(String serviceName, String host, int port);
}
