package site.elseif.myRpcFramework.core.server;// rpc-core/src/main/java/site/elseif/rpc/core/server/site.elseif.myRpcFramwork.core.server.NettyServer.java

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import lombok.extern.slf4j.Slf4j;
import site.elseif.myRpcFramework.core.codec.RpcMessageDecoder;
import site.elseif.myRpcFramework.core.codec.RpcMessageEncoder;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class NettyServer {

    private final String host;
    private final int port;
    private final Map<String, Object> serviceMap = new ConcurrentHashMap<>();

    public NettyServer(String host, int port) {
        this.host = host;
        this.port = port;
    }

    /**
     * 注册服务
     * @param serviceName 接口名
     * @param serviceImpl 实现类实例
     */
    public void registerService(String serviceName, Object serviceImpl) {
        serviceMap.put(serviceName, serviceImpl);
        log.info("注册服务：{} -> {}", serviceName, serviceImpl.getClass().getName());
    }

    /**
     * 启动服务器
     */
    public void start() throws InterruptedException {
        // 1. 创建两个线程组
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();

        try {
            // 2. 创建启动引导类
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .handler(new LoggingHandler(LogLevel.INFO))
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline()
                                    // 解码器：处理粘包拆包
                                    .addLast(new RpcMessageDecoder())
                                    // 编码器
                                    .addLast(new RpcMessageEncoder())
                                    // 业务处理器
                                    .addLast(new NettyServerHandler(serviceMap));
                        }
                    })
                    // 优化TCP参数
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childOption(ChannelOption.SO_KEEPALIVE, true);

            // 3. 绑定端口启动
            ChannelFuture future = bootstrap.bind(host, port).sync();
            log.info("Netty服务器启动成功：{}:{}", host, port);

            // 4. 等待服务端关闭
            future.channel().closeFuture().sync();

        } finally {
            // 5. 优雅关闭
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}