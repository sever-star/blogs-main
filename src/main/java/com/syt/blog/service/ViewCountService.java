package com.syt.blog.service;

import com.syt.blog.repository.PostRepository;
import com.syt.blog.util.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
public class ViewCountService {
    /** 去重时间窗：同一访客对同一篇文章 30 分钟内只计 1 次 */
    private static final Duration DEDUP_WINDOW = Duration.ofMinutes(30);
    /** 单 IP 限速：每分钟最多计 10 次 */
    private static final int RATE_LIMIT_PER_MINUTE = 10;
    /** 触发限速后拉黑该 IP 10 分钟 */
    //Duration.ofMinutes是javaAPI，用于创建一个Duration对象，表示时间
    private static final Duration BAN_DURATION = Duration.ofMinutes(10);
    private static final String KEY_DEDUP = "blog:view:dedup:";   // {postId}:{visitorKey}
    private static final String KEY_RATE  = "blog:view:rate:";    // {ip}
    private static final String KEY_BAN   = "blog:view:ban:";     // {ip}
    private final StringRedisTemplate redis;
    private final PostRepository postRepository;
    public ViewCountService(StringRedisTemplate redis, PostRepository postRepository) {
        this.redis = redis;
        this.postRepository = postRepository;
    }
    /**
     * 记一次浏览。放在文章详情查询之后调用，不影响接口返回。
     *
     * @param postId     文章 ID
     * @param authorId   文章作者 ID（作者本人不计数）
     * @param request    当前请求（取 IP 和 User-Agent）
     */
    public void recordView(Integer postId, Integer authorId, HttpServletRequest request) {
        if(request==null){return;}
        try {
            // 从请求头中获取 IP 地址
            String ip = resolveIp(request);
            // 从请求头中获取 User-Agent防止爬虫UA被忽略
            String userAgent = request.getHeader("User-Agent");
            // 闸 1：爬虫 UA 直接忽略
            if (isBot(userAgent)) {
                return;
            }
            // 闸 2：作者本人浏览不计数
            Integer currentUserId = SecurityUtils.currentUser() != null
                    ? SecurityUtils.currentUser().id() : null;
            if (currentUserId != null && currentUserId.equals(authorId)) {
                return;
            }
            // 闸 3：黑名单 IP 直接忽略
            //hasKey()方法：判断指定键是否存在
            //返回值：boolean（有就 true，没有就 false）
            if (Boolean.TRUE.equals(redis.hasKey(KEY_BAN + ip))) {
                return;
            }
            // 闸 4：单 IP 限速，超了就拉黑
            String rateKey = KEY_RATE + ip;
            Long count = redis.opsForValue().increment(rateKey);
            if (count != null && count == 1L) {
                redis.expire(rateKey, Duration.ofSeconds(60));
            }
            if (count != null && count > RATE_LIMIT_PER_MINUTE) {
                redis.opsForValue().set(KEY_BAN + ip, "1", BAN_DURATION);
                log.warn("IP 触发浏览限速并拉黑: {}", ip);
                return;
            }
            // 闸 5：时间窗去重（核心）。SET NX EX 原子占位，
            // false = 30 分钟内已计过，true = 第一次，放行计数。
            String visitorKey = currentUserId != null ? "u" + currentUserId : "ip" + ip;
            Boolean first = redis.opsForValue().setIfAbsent(
                    KEY_DEDUP + postId + ":" + visitorKey, "1", DEDUP_WINDOW);
            if (!Boolean.TRUE.equals(first)) {
                return;
            }
            // 全部通过：原子 +1（PostRepository 里现成的方法）
            postRepository.updateViewCount(postId);
        } catch (Exception e) {
            // fail-open：Redis 超时/宕机时放行计数，浏览量宁多勿错，详情接口不能挂
            log.warn("防刷检查失败，降级为直接计数: postId={}, 原因={}", postId, e.getMessage());
            try {
                postRepository.updateViewCount(postId);
            } catch (Exception ex) {
                log.warn("浏览量计数失败: postId={}", postId, ex);
            }
        }
    }
    /** 取真实 IP：优先 X-Forwarded-For（有反向代理时），否则取直连地址 */
    private String resolveIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            // XFF 取第一个，防伪造的放在最后取 remoteAddr 的场景自行取舍
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
    /** 粗粒度 bot 识别 */
    private boolean isBot(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return true; // 没有 UA 的一律不算
        }
        String ua = userAgent.toLowerCase();
        //检查当前字符串里有没有包含参数的子串
        //返回值：boolean（有就 true，没有就 false）
        return ua.contains("bot") || ua.contains("spider") || ua.contains("crawler")
                || ua.contains("scraper") || ua.contains("curl") || ua.contains("wget");
    }
}
