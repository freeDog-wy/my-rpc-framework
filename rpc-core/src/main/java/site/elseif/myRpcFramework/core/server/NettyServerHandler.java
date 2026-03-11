package site.elseif.myRpcFramework.core.server;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;
import site.elseif.myRpcFramework.common.*;
import site.elseif.myRpcFramework.common.exception.CircuitBreakerException;
import site.elseif.myRpcFramework.common.exception.FlowLimitException;
import site.elseif.myRpcFramework.common.group.ServiceGroup;
import site.elseif.myRpcFramework.core.circuit.CircuitBreaker;
import site.elseif.myRpcFramework.core.circuit.CircuitBreakerManager;
import site.elseif.myRpcFramework.core.flow.FlowController;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class NettyServerHandler extends SimpleChannelInboundHandler<RpcProtocol<RpcRequest>> {

    /**
     * 本地服务注册表：存储接口名 -> 实现类实例
     * 例如：{"site.elseif.rpc.api.HelloService": helloServiceImpl}
     */
    private final Map<String, Object> serviceMap;
    private final ServiceGroup serviceGroup;
    private final GroupedThreadPool groupedThreadPool = new GroupedThreadPool();
    private final CircuitBreakerManager circuitBreakerManager;
    private final FlowController flowController;
    private final byte serializerType;
    /**
     * 构造函数，传入服务注册表
     */
    public NettyServerHandler(Map<String, Object> serviceMap
            , ServiceGroup serviceGroup
            , FlowController flowController
            , CircuitBreakerManager circuitBreakerManager
            , byte serializerType) {

        this.serviceMap = Objects.requireNonNullElseGet(serviceMap, ConcurrentHashMap::new);
        this.serviceGroup = Objects.requireNonNullElseGet(serviceGroup, ServiceGroup::new);
        this.flowController = Objects.requireNonNullElseGet(flowController, FlowController::new);
        this.circuitBreakerManager = Objects.requireNonNullElseGet(circuitBreakerManager, CircuitBreakerManager::new);
        this.serializerType = serializerType;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, RpcProtocol<RpcRequest> msg) throws Exception  {
        // 打印日志，方便调试
        log.info("服务端接收到消息：{}", msg);

        // 1. 解析消息，拿到接口名、方法名、参数
        RpcRequest request = msg.getBody();
        String serviceName = request.getInterfaceName();
        String methodName = request.getMethodName();
        String requestId = request.getRequestId();

        log.info("收到RPC请求：接口={}，方法={}，请求ID={}", serviceName, methodName, requestId);


        // 2. 熔断检查（自动使用配置的规则）
        CircuitBreaker breaker = circuitBreakerManager.getBreaker(serviceName, methodName);
        if (!breaker.allowRequest()) {
            log.warn("请求被熔断：{}#{}", serviceName, methodName);
            sendErrorResponse(ctx, requestId, new CircuitBreakerException(true, serviceName, methodName));
            return;
        }

        // 3. 限流检查（自动使用配置的规则）
        if (!flowController.tryAcquire(serviceName, methodName)) {
            log.warn("请求被限流：{}#{}", serviceName, methodName);
            sendErrorResponse(ctx, requestId, new FlowLimitException(true, serviceName, methodName));
            return;
        }

        // 4. 获取服务分组
        ServiceGroup.Group group = serviceGroup.getGroup(serviceName);

        groupedThreadPool.submit(group, () -> {
            try {
                RpcResponse response = handleRequest(request);
                // 5. 构建响应消息并写回客户端
                RpcProtocol<RpcResponse> responseRpcProtocol = buildProtocol(response);
                // 发送响应
                ctx.writeAndFlush(responseRpcProtocol);
                breaker.recordSuccess();
            }  catch (Exception e) {
                log.error("处理请求失败", e);
                breaker.recordFailure();
                sendErrorResponse(ctx, requestId, e);
            }
        });
    }

    private void sendErrorResponse(ChannelHandlerContext ctx, String requestId, Throwable error) {
        RpcResponse response = new RpcResponse();
        response.setRequestId(requestId);
        response.setError(error);
        ctx.writeAndFlush(buildProtocol(response));
    }

    private RpcResponse handleRequest(RpcRequest request) {
        // 1. 创建响应对象
        RpcResponse response = new RpcResponse();
        String serviceName = request.getInterfaceName();
        String methodName = request.getMethodName();
        Object[] parameters = request.getParameters();
        Class<?>[] parameterTypes = request.getParameterTypes();
        response.setRequestId(request.getRequestId());

        try {
            // 2. 根据接口名从本地serviceMap中找到实现类实例
            Object serviceInstance = serviceMap.get(serviceName);
            if (serviceInstance == null) {
                throw new RuntimeException("未找到服务实现类：" + serviceName);
            }

            log.info("找到服务实现类：{}", serviceInstance.getClass().getName());

            // 3. 反射调用方法
            Class<?> serviceClass = serviceInstance.getClass();
            Method method = serviceClass.getMethod(methodName, parameterTypes);
            Object result = method.invoke(serviceInstance, parameters);

            log.info("方法调用成功，结果：{}", result);

            // 4. 封装结果
            response.setResult(result);
            response.setError(null);

        } catch (Exception e) {
            log.error("方法调用失败", e);
            throw new RuntimeException(e);
        }
        return response;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("处理器异常", cause);
        ctx.close();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        log.info("客户端断开连接：{}", ctx.channel().remoteAddress());
        super.channelInactive(ctx);
    }

    private RpcProtocol<RpcResponse> buildProtocol(RpcResponse response) {
        MessageHeader header = new MessageHeader();
        header.setMagic(MessageConstants.MAGIC_NUMBER);
        header.setVersion(MessageConstants.VERSION);
        header.setMessageType(MessageConstants.RESPONSE);
        header.setSerializerType(serializerType);
        header.setMessageId(fixedLength(response.getRequestId(), 32));
        // dataLength会在编码器中自动设置

        RpcProtocol<RpcResponse> protocol = new RpcProtocol<>();
        protocol.setHeader(header);
        protocol.setBody(response);
        return protocol;
    }

    private String fixedLength(String str, int length) {
        if (str.length() >= length) {
            return str.substring(0, length);
        }
        StringBuilder sb = new StringBuilder(str);
        while (sb.length() < length) {
            sb.append(' ');
        }
        return sb.toString();
    }
}