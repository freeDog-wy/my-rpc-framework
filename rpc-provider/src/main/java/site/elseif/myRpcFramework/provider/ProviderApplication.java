package site.elseif.myRpcFramework.provider;

import lombok.extern.slf4j.Slf4j;
import site.elseif.myRpcFramework.api.HelloService;
import site.elseif.myRpcFramework.common.MessageConstants;
import site.elseif.myRpcFramework.core.registry.NacosServiceRegistry;
import site.elseif.myRpcFramework.core.server.NettyServer;

@Slf4j
public class ProviderApplication {
    public static void main(String[] args) throws InterruptedException {
        // 1. 创建服务器实例
        NettyServer server = new NettyServer("127.0.0.1", 8845, MessageConstants.SERIALIZER_KRYO);
        log.info("服务提供者启动，监听地址：");
        // 2. 创建服务实现类
        HelloService helloService = new HelloServiceImpl();

        // 3. 注册服务
        server.registerService(HelloService.class.getName(), helloService);

        NacosServiceRegistry serviceRegistry = new NacosServiceRegistry("127.0.0.1:8848", "public");

//        server.enableNacosConfig("127.0.0.1:8848", "service-rules.yaml", "DEFAULT_GROUP");

        serviceRegistry.registerService("site.elseif.myRpcFramework.api.HelloService", "127.0.0.1", 8845);

        // 4. 启动服务器
        server.start();

        serviceRegistry.deregisterService("site.elseif.myRpcFramework.api.HelloService", "127.0.0.1", 8845);
    }
}