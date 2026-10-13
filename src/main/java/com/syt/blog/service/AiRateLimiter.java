package com.syt.blog.service;

import com.syt.blog.common.BusinessException;
import com.syt.blog.common.ErrorCode;
import com.syt.blog.config.AiProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 游客提问频率限制（滑动窗口）。
 * <p>
 * AI 页面允许游客使用，但每次提问都要消耗 token 产生真实费用，因此按 IP 限流。
 * 登录用户不限制 —— 已有账号体系可追溯，无需再叠一层。
 * <p>
 * 计数保存在内存里：单实例部署足够；将来多实例需换成 Redis 计数，
 * 否则限流额度会随实例数放大。
 */
@Component
@RequiredArgsConstructor
public class AiRateLimiter {

    /** 窗口长度：1 小时 */
    private static final long WINDOW_MILLIS = Duration.ofHours(1).toMillis();

    /** 触发机会式清理的 Map 规模阈值 */
    private static final int CLEANUP_THRESHOLD = 10_000;

    private final AiProperties properties;

    /** IP -> 窗口内各次提问的时间戳（升序） */
    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    /**
     * 记一次提问并校验额度，超限抛 {@link BusinessException}。
     *
     * @param ip 客户端 IP
     */
    public void acquire(String ip) {
        int limit = properties.getGuestHourlyLimit();
        if (limit <= 0) {
            return; // 配置为 0 或负数表示关闭限流
        }

        long now = System.currentTimeMillis();
        long windowStart = now - WINDOW_MILLIS;

        Deque<Long> timestamps = hits.computeIfAbsent(ip, key -> new ArrayDeque<>());
        synchronized (timestamps) {
            // 先淘汰滑出窗口的记录，再判断额度
            while (!timestamps.isEmpty() && timestamps.peekFirst() < windowStart) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= limit) {
                throw new BusinessException(ErrorCode.AI_RATE_LIMITED,
                        "提问过于频繁，请稍后再试（每小时最多 " + limit + " 次）");
            }
            timestamps.addLast(now);
        }

        if (hits.size() > CLEANUP_THRESHOLD) {
            evictIdleEntries(windowStart);
        }
    }

    /** 清理窗口内已无记录的空桶，避免长期运行后 Map 无限增长 */
    private void evictIdleEntries(long windowStart) {
        hits.entrySet().removeIf(entry -> {
            Deque<Long> timestamps = entry.getValue();
            synchronized (timestamps) {
                while (!timestamps.isEmpty() && timestamps.peekFirst() < windowStart) {
                    timestamps.pollFirst();
                }
                return timestamps.isEmpty();
            }
        });
    }
}
