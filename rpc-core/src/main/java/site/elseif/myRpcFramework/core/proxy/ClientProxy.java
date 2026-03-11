package site.elseif.myRpcFramework.core.proxy;

import site.elseif.myRpcFramework.common.MessageConstants;
import site.elseif.myRpcFramework.common.RpcRequest;
import site.elseif.myRpcFramework.common.RpcResponse;
import site.elseif.myRpcFramework.common.exception.CircuitBreakerException;
import site.elseif.myRpcFramework.common.exception.FlowLimitException;
import site.elseif.myRpcFramework.common.exception.RpcErrorCode;
import site.elseif.myRpcFramework.common.exception.RpcException;
import site.elseif.myRpcFramework.core.circuit.CircuitBreaker;
import site.elseif.myRpcFramework.core.client.NettyClient;
import site.elseif.myRpcFramework.core.config.NacosConfigManager;
import site.elseif.myRpcFramework.core.config.NacosConsumerConfigManager;
import site.elseif.myRpcFramework.core.config.ServiceConfigManager;
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

    private final ServiceConfigManager serviceConfigManager = new ServiceConfigManager();
    private NacosConfigManager nacosConfigManager;

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

    public void enableNacosConfig(String nacosServerAddr, String dataId, String group) {
        nacosConfigManager = new NacosConsumerConfigManager(nacosServerAddr, dataId, group, serviceConfigManager);
        log.info("已启用Nacos配置中心：{}", nacosServerAddr);
        serviceConfigManager.applyConfigs();
        log.info("已应用Nacos配置");
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
        String serviceName = method.getDeclaringClass().getName();
        String methodName = method.getName();

        log.info("调用方法：{}#{}({})",
                serviceName,
                methodName,
                args);
        // 1. 客户端限流检查
        if (!serviceConfigManager.getFlowController().tryAcquire(serviceName, methodName)) {
            throw new FlowLimitException(false, serviceName, methodName);
        }
        // 2. 熔断检查
        CircuitBreaker breaker = serviceConfigManager.getCircuitBreakerManager().getBreaker(serviceName, methodName);
        if (breaker != null && !breaker.allowRequest()) {
            throw new CircuitBreakerException(false, serviceName, methodName);
        }
        // 3. 构建RPC请求
        RpcRequest request = buildRequest(method, args);

        // 4. 从注册中心获取服务地址
        ServiceInstance instance = serviceDiscovery.discover(serviceName, loadBalancer);

        // 5. 发送RPC请求
        RpcResponse response = nettyClient.sendRequest(instance, request);

        // 6. 处理响应
        if (response.getError() != null) {
            handleException(response, serviceName, methodName);
        }

        return response.getResult();
    }

    private void handleException(RpcResponse response, String serviceName, String methodName) throws Throwable {
        Throwable error = response.getError();

        // 根据异常类型做不同处理
        if (error instanceof CircuitBreakerException) {
            // 服务端熔断，触发客户端的熔断记录
            serviceConfigManager.getCircuitBreakerManager()
                    .getBreaker(serviceName, methodName)
                    .recordFailure();
        } else if (error instanceof FlowLimitException) {
            // 服务端限流，可以等待后重试
//            if (((FlowLimitException) error).getCode().isRetryable()) {
//                Thread.sleep(100);  // 等待100ms后重试
//                return retryWithBackoff(method, args);
//            }

        } else if (error instanceof RpcException) {
            // 其他RPC异常
            RpcErrorCode code = ((RpcException) error).getCode();
            log.error("RPC调用失败：{} - {}", code.getCode(), code.getMessage());

//            if (code.isRetryable()) {
//                return retryWithAnotherInstance(method, args);
//            }
        }

        throw error;
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