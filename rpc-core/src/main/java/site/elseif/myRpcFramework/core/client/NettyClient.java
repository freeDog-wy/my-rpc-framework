package site.elseif.myRpcFramework.core.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import lombok.extern.slf4j.Slf4j;
import site.elseif.myRpcFramework.common.*;
import site.elseif.myRpcFramework.common.exception.RpcErrorCode;
import site.elseif.myRpcFramework.common.exception.RpcException;
import site.elseif.myRpcFramework.core.discovery.ServiceInstance;
import site.elseif.myRpcFramework.core.codec.RpcMessageDecoder;
import site.elseif.myRpcFramework.core.codec.RpcMessageEncoder;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
public class NettyClient {

    // Netty的核心组件
    private final EventLoopGroup eventLoopGroup;  // 事件循环组，处理I/O操作
    private final Bootstrap bootstrap;            // 客户端启动引导类
    private final ChannelProvider channelProvider; // 自定义的通道提供者，管理连接
    private final byte serializerType;             // 序列化类型
    private volatile boolean isShutdown = false;     // 是否已关闭

    public NettyClient() {
        this(MessageConstants.SERIALIZER_KRYO);  // 默认使用Kryo序列化
    }

    public NettyClient(byte serializerType) {
        this.eventLoopGroup = new NioEventLoopGroup();  // 创建NIO事件循环组
        this.bootstrap = new Bootstrap();                // 创建启动引导类
        this.channelProvider = new ChannelProvider();    // 创建通道提供者
        this.serializerType = serializerType;            // 设置序列化类型

        // 配置Netty客户端
        bootstrap.group(eventLoopGroup)                  // 设置事件循环组
                .channel(NioSocketChannel.class)         // 设置通道类型为NIO
                .option(ChannelOption.TCP_NODELAY, true) // 禁用Nagle算法，提高实时性
                .option(ChannelOption.SO_KEEPALIVE, true) // 开启TCP keepalive
                .option(ChannelOption.ALLOCATOR,  PooledByteBufAllocator.DEFAULT) // 开启 Netty 内存池
                .handler(new ChannelInitializer<SocketChannel>() {  // 设置通道初始化器
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        // 配置管道中的处理器链
                        ch.pipeline()
                                .addLast(new RpcMessageDecoder())  // RPC消息解码器
                                .addLast(new RpcMessageEncoder())  // RPC消息编码器
                                .addLast(new NettyClientHandler()); // 客户端业务处理器
                    }
                });
    }

    /**
     * 发送RPC请求
     * 这是Netty使用的核心方法，展示了如何通过Netty进行网络通信
     */
    public RpcResponse sendRequest(ServiceInstance instance, RpcRequest request) {
        if (isShutdown) {
            throw new IllegalStateException("客户端已关闭");
        }
        String address = instance.getIp() + ":" + instance.getPort();

        try {
            // 1. 获取或创建Channel（Netty网络连接的抽象）
            // 使用ChannelProvider管理连接，避免重复创建
            Channel channel = channelProvider.get(address, () -> {
                try {
                    // 异步连接到服务器
                    ChannelFuture future = bootstrap.connect(instance.getIp(), instance.getPort()).sync();
                    return future.channel();
                } catch (Exception e) {
                    log.error("连接服务器失败：{}", address, e);
                    throw new RuntimeException(e);
                }
            });

            // 2. 创建CompletableFuture用于异步等待响应
            // 这是Java 8的异步编程工具，用于处理异步结果
            CompletableFuture<RpcResponse> resultFuture = new CompletableFuture<>();
            // 将请求ID和Future关联，以便收到响应时可以找到对应的等待者
            NettyClientHandler.addPendingRequest(request.getRequestId(), resultFuture);

            // 3. 构建协议消息
            RpcProtocol<RpcRequest> protocol = buildProtocol(request);

            // 4. 发送请求（Netty的核心操作：异步写入）
            channel.writeAndFlush(protocol).addListener((ChannelFutureListener) future -> {
                // 添加监听器处理发送结果
                if (!future.isSuccess()) {
                    log.error("发送消息失败", future.cause());
                    resultFuture.completeExceptionally(future.cause());
                }
            });

            // 5. 等待响应（设置5秒超时）
            // 这里展示了如何将异步的Netty通信转为同步等待
            return resultFuture.get(5, java.util.concurrent.TimeUnit.SECONDS);

        } catch (Exception e) {
            log.error("RPC调用失败", e);
            throw new RpcException(RpcErrorCode.CONNECTION_ERROR, "RPC调用失败");
        }
    }

    /**
     * 构建协议消息
     * 展示了如何在Netty中构建自定义协议
     */
    private RpcProtocol<RpcRequest> buildProtocol(RpcRequest request) {
        MessageHeader header = new MessageHeader();
        header.setMagic(MessageConstants.MAGIC_NUMBER);        // 魔数，用于协议识别
        header.setVersion(MessageConstants.VERSION);            // 协议版本
        header.setMessageType(MessageConstants.REQUEST);       // 消息类型（请求/响应）
        header.setSerializerType(serializerType);               // 序列化类型
        header.setMessageId(fixedLength(request.getRequestId(), 32)); // 固定长度的消息ID
        // dataLength会在编码器中自动设置，体现了Netty的责任链模式

        RpcProtocol<RpcRequest> protocol = new RpcProtocol<>();
        protocol.setHeader(header);
        protocol.setBody(request);
        return protocol;
    }

    /**
     * 固定消息ID长度为32字节
     * 用于确保协议格式统一
     */
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

    /**
     * 关闭客户端
     */
    public void close() {
        if (isShutdown) {
            return;
        }
        isShutdown = true;
        log.info("正在关闭Netty客户端...");
        channelProvider.closeAll();  // 关闭所有Channel连接
        eventLoopGroup.shutdownGracefully(2, 15, TimeUnit.SECONDS)
                .addListener(future -> {
                    if (future.isSuccess()) {
                        log.info("事件循环组已优雅关闭");
                    } else {
                        log.error("事件循环组关闭失败", future.cause());
                    }
                });  // 优雅关闭事件循环组
        log.info("Netty客户端已关闭");
    }
}