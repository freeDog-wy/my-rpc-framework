package site.elseif.myRpcFramework.core.client;

import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Slf4j
public class ChannelProvider {

    private final Map<String, Channel> channelMap = new ConcurrentHashMap<>();

    /**
     * 获取Channel，如果不存在或不可用则创建新的
     */
    public Channel get(String address, Supplier<Channel> supplier) {
        Channel channel = channelMap.get(address);

        // 如果channel不存在或不可用，创建新的
        if (channel == null || !channel.isActive()) {
            log.info("创建新的Channel：{}", address);
            channel = supplier.get();
            channelMap.put(address, channel);
        }

        return channel;
    }

    /**
     * 移除Channel
     */
    public void remove(String address) {
        channelMap.remove(address);
        log.info("移除Channel：{}", address);
    }

    /**
     * 关闭所有Channel
     */
    public void closeAll() {
        log.info("关闭所有Channel...");
        channelMap.values().forEach(channel -> {
            try {
                if (channel.isActive()) {
                    channel.close().sync();
                    log.info("关闭Channel：{}", channel.remoteAddress());
                }
            } catch (InterruptedException e) {
                log.error("关闭Channel失败：{}", channel.remoteAddress(), e);
            }
        });
        channelMap.clear();
    }
}