package site.elseif.myRpcFramework.core.flow;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 令牌桶限流器
 */
@Slf4j
public class RateLimiter {

    private final long capacity;           // 桶容量
    private final long rate;                // 令牌生成速率（每秒）
    private final AtomicLong tokens;         // 当前令牌数
    private final AtomicLong lastRefillTime; // 上次补充令牌时间

    public RateLimiter(long capacity, long rate) {
        this.capacity = capacity;
        this.rate = rate;
        this.tokens = new AtomicLong(capacity);
        this.lastRefillTime = new AtomicLong(System.currentTimeMillis());
    }

    /**
     * 尝试获取令牌
     * @return 是否获取成功
     */
    public boolean tryAcquire() {
        refillTokens();
        return tokens.getAndDecrement() > 0;
    }

    /**
     * 补充令牌
     */
    private void refillTokens() {
        long now = System.currentTimeMillis();
        long last = lastRefillTime.get();

        if (now > last) {
            long elapsed = now - last;
            long newTokens = (elapsed * rate) / 1000; // 根据时间计算应补充的令牌

            if (newTokens > 0) {
                long currentTokens = tokens.get();
                long updatedTokens = Math.min(capacity, currentTokens + newTokens);
                tokens.compareAndSet(currentTokens, updatedTokens);
                lastRefillTime.compareAndSet(last, now);
            }
        }
    }

    /**
     * 获取剩余令牌数
     */
    public long getAvailableTokens() {
        refillTokens();
        return tokens.get();
    }
}