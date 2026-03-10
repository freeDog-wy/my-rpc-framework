package site.elseif.myRpcFramework.core.client;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;
import site.elseif.myRpcFramework.common.RpcProtocol;
import site.elseif.myRpcFramework.common.RpcResponse;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
@Slf4j
public class NettyClientHandler extends SimpleChannelInboundHandler<RpcProtocol<RpcResponse>> {

    /**
     * 存储未完成的请求：requestId -> CompletableFuture
     */
    private static final Map<String, CompletableFuture<RpcResponse>> PENDING_REQUESTS = new ConcurrentHashMap<>();

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, RpcProtocol<RpcResponse> protocol) {
        RpcResponse response = protocol.getBody();
        String requestId = response.getRequestId();

        log.info("收到服务端响应：requestId={}", requestId);

        // 取出并完成对应的Future
        CompletableFuture<RpcResponse> future = PENDING_REQUESTS.remove(requestId);
        if (future != null) {
            future.complete(response);
        } else {
            log.warn("未找到对应的请求：{}", requestId);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("客户端异常", cause);
        ctx.close();
    }

    /**
     * 添加待处理的请求
     */
    public static void addPendingRequest(String requestId, CompletableFuture<RpcResponse> future) {
        PENDING_REQUESTS.put(requestId, future);
    }
}