package site.elseif.myRpcFramework.core.proxy;

import site.elseif.myRpcFramework.common.MessageConstants;
import site.elseif.myRpcFramework.common.RpcRequest;
import site.elseif.myRpcFramework.common.RpcResponse;
import site.elseif.myRpcFramework.core.client.NettyClient;
import site.elseif.myRpcFramework.core.discovery.ManualServiceDiscovery;
import site.elseif.myRpcFramework.core.discovery.ServiceDiscovery;
import site.elseif.myRpcFramework.core.discovery.ServiceInstance;
import site.elseif.myRpcFramework.core.loadBalance.LoadBalancer;
import site.elseif.myRpcFramework.core.loadBalance.RandomLoadBalancer;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.UUID;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ClientProxy implements InvocationHandler {

    private final ServiceDiscovery serviceDiscovery;
    private final LoadBalancer loadBalancer;
    private final NettyClient nettyClient;

    public ClientProxy() {
        this(MessageConstants.SERIALIZER_KRYO       // 默认使用Kryo序列化
                , new ManualServiceDiscovery());    // 默认使用手动服务发现
    }
    public ClientProxy(byte serializerType, ServiceDiscovery serviceDiscovery) {
        log.info("创建ClientProxy，使用序列化方式：{}", serializerType);
        // this.serviceDiscovery = new discovery.NacosServiceDiscovery("localhost:8848");
        // 使用写死的服务发现
        this.serviceDiscovery = serviceDiscovery;
        // 使用随机负载均衡
        this.loadBalancer = new RandomLoadBalancer();

        this.nettyClient = new NettyClient(serializerType);
    }

    /**
     * 创建代理对象
     */
    @SuppressWarnings("unchecked")
    public <T> T getProxy(Class<T> clazz) {
        return (T) Proxy.newProxyInstance(
                clazz.getClassLoader(),
                new Class[]{clazz},
                this
        );
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        log.info("调用方法：{}#{}({})",
                method.getDeclaringClass().getName(),
                method.getName(),
                args);

        // 1. 构建RPC请求
        RpcRequest request = buildRequest(method, args);

        // 2. 从注册中心获取服务地址
        String serviceName = method.getDeclaringClass().getName();
        ServiceInstance instance = serviceDiscovery.discover(serviceName, loadBalancer);

        // 3. 发送RPC请求
        RpcResponse response = nettyClient.sendRequest(instance, request);

        // 4. 处理响应
        if (response.getError() != null) {
            throw new RuntimeException("RPC调用失败", response.getError());
        }

        return response.getResult();
    }

    /**
     * 构建RPC请求
     */
    private RpcRequest buildRequest(Method method, Object[] args) {
        RpcRequest request = new RpcRequest();
        request.setRequestId(UUID.randomUUID().toString());
        request.setInterfaceName(method.getDeclaringClass().getName());
        request.setMethodName(method.getName());
        request.setParameterTypes(method.getParameterTypes());
        request.setParameters(args);
        return request;
    }

    public void close() {
        log.info("关闭ClientProxy资源");
        if (nettyClient != null) {
            nettyClient.close();
        }
        if (serviceDiscovery != null) {
            serviceDiscovery.close();
        }
        log.info("ClientProxy资源已关闭");
    }
}