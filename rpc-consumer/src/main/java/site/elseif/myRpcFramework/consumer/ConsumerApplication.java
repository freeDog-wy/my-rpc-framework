package site.elseif.myRpcFramework.consumer;

import site.elseif.myRpcFramework.api.HelloService;
import site.elseif.myRpcFramework.common.MessageConstants;
import site.elseif.myRpcFramework.core.discovery.NacosListenerServiceDiscovery;
import site.elseif.myRpcFramework.core.discovery.NacosServiceDiscovery;
import site.elseif.myRpcFramework.core.discovery.CachingServiceDiscovery;
import site.elseif.myRpcFramework.core.proxy.ClientProxy;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ConsumerApplication {
    public static void main(String[] args) {
        log.info("消费者启动...");

//        NacosServiceDiscovery serviceDiscovery = new NacosServiceDiscovery("127.0.0.1:8848", "public");
        NacosListenerServiceDiscovery serviceDiscovery = new NacosListenerServiceDiscovery("127.0.0.1", "public");
//        CachingServiceDiscovery cachingServiceDiscovery = new CachingServiceDiscovery(serviceDiscovery, 30 * 1000); // 缓存30秒

        // 1. 创建代理对象
        ClientProxy proxy = new ClientProxy(MessageConstants.SERIALIZER_HESSIAN, serviceDiscovery);
        HelloService helloService = proxy.getProxy(HelloService.class);

        // 2. 调用远程方法
        try {
            for (int i = 0; i < 10; ++i) {
                String result = helloService.sayHello("World");
                log.info("调用结果：{}", result);

                // 测试另一个方法
                String result2 = helloService.sayHello("Java", 30);
                log.info("调用结果2：{}", result2);
                Thread.sleep(5000);
            }
        } catch (Exception e) {
            log.error("调用失败", e);
        } finally {
            // 3. 关闭客户端资源
            proxy.close();
            log.info("消费者已关闭");
        }
    }
}