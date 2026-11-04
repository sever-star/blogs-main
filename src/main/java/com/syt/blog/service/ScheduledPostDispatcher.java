package com.syt.blog.service;

import com.syt.blog.repository.PostRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RDelayedQueue;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;


@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduledPostDispatcher {
    private final RedissonClient redisson;
    private final PostRepository postRepository;

    /** 单篇发布失败时的最大重试次数 */
    private static final int MAX_RETRY = 3;
    /** 重试仍失败后，重新入队的延迟秒数（避免丢任务，给 DB / 下游恢复时间） */
    private static final long RETRY_DELAY_SECONDS = 30;
    /** 优雅关闭时等待消费线程收尾的最长时间（毫秒） */
    private static final long SHUTDOWN_JOIN_MILLIS = 2000;

    private RBlockingQueue<Integer> queue;
    private RDelayedQueue<Integer> delayed;
    /** 持有消费线程引用，供 @PreDestroy 中断收尾 */
    private Thread consumer;

    @PostConstruct
    void init() {
        //创建就绪队列
        queue = redisson.getBlockingQueue("blog:scheduled:queue");
        //创建阻塞队列
        delayed = redisson.getDelayedQueue(queue);
        // 启动恢复：从 DB 重新 offer 所有未到点的定时稿
        postRepository.findPendingScheduled().forEach(p -> {
            //计算还有距离发布还有多少秒
            long delay = Duration.between(LocalDateTime.now(), p.getScheduledAt()).getSeconds();
            if (delay <= 0) {
                // 停机期间已到点：直接发布
                log.info("启动恢复发现已到点定时稿，立即发布 postId={}", p.getId());
                publishWithRetry(p.getId());
            } else {
                //未到时间存入阻塞队列
                offerOrLog(p.getId(), delay, "启动恢复");
            }
        });
        consumer = Thread.ofPlatform().daemon(true).name("scheduled-publish").start(this::consume);
    }

    /**
     * 审核通过且定时在未来 → 入队；若定时已到（审核刚通过但时间已过）→ 立即发布。
     * 调用方：approvePost 在判定 scheduledAt 非空时调用。
     */
    public void schedule(Integer postId, LocalDateTime at) {
        if (postId == null || at == null) {
            return;
        }
        long delay = Duration.between(LocalDateTime.now(), at).getSeconds();
        if (delay <= 0) {
            // 定时时间已过：立即发布，避免负 delay 入队语义不清
            log.info("定时时间已到，立即发布 postId={}", postId);
            publishWithRetry(postId);
            return;
        }
        offerOrLog(postId, delay, "定时入队");
    }

    /**
     * 取消定时：从延迟队列移除 postId。
     * 调用方：updatePost 改了 scheduledAt / 重新提审(review→0)、deletePost 软删时调用，
     * 否则已失效的 postId 仍留在队列里，到点会触发一次无意义的 publish（虽然幂等无害，但应清理）。
     */
    public void cancel(Integer postId) {
        if (postId == null || delayed == null) {
            return;
        }
        try {
            delayed.remove(postId);
        } catch (Exception e) {
            log.warn("移除定时队列失败 postId={}", postId, e);
        }
    }

    /** 消费循环：阻塞取到期任务，发布失败则重试，重试仍失败则回投队列避免丢任务 */
    private void consume() {
        while (!Thread.currentThread().isInterrupted()) {
            Integer postId;
            try {
                postId = queue.take();   // 阻塞到到期；被中断时抛 InterruptedException
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            try {
                if (!publishWithRetry(postId)) {
                    // take 已把任务移出队列，重试仍失败时必须回投，否则永久丢失
                    boolean reoffered = offerOrLog(postId, RETRY_DELAY_SECONDS, "重试回投");
                    if (reoffered) {
                        log.error("定时发布重试 {} 次仍失败，已回投队列 {}s 后重试 postId={}",
                                MAX_RETRY, RETRY_DELAY_SECONDS, postId);
                    } else {
                        log.error("定时发布重试 {} 次仍失败且回投也失败 postId={}，将依赖 DB 兜底",
                                MAX_RETRY, postId);
                    }
                }
            } catch (Exception e) {
                // 兜底：循环内任何意外都不能让消费线程死掉
                log.error("定时发布消费异常 postId={}", postId, e);
            }
        }
        log.info("定时发布消费线程退出");
    }

    /**
     * DB 兜底扫描：每分钟扫一遍"已到点但还没发布"的定时稿并补发。
     * <p>
     * 正常情况下消费线程已及时发布，此查询返回空、开销极小；只在 Redis 队列丢任务、
     * offer 失败、消费线程异常时才有数据。{@code publishScheduledPost} 幂等（WHERE status=0），
     * 与消费线程重复触发同一篇也安全——最坏两条路径各执行一次 UPDATE，第二条 affected=0。
     */
    @Scheduled(fixedDelay = 60_000L, initialDelay = 60_000L)
    void sweepOverdue() {
        try {
            List<Integer> ids = postRepository.findOverdueScheduled();
            if (ids.isEmpty()) {
                return;
            }
            log.warn("DB 兜底扫描到 {} 篇逾期未发定时稿，开始补发", ids.size());
            for (Integer id : ids) {
                if (!publishWithRetry(id)) {
                    log.error("DB 兜底补发仍失败 postId={}，下一轮继续重试", id);
                }
            }
        } catch (Exception e) {
            // 扫描本身不能挂，任何异常都吞掉等下一轮
            log.error("DB 兜底扫描异常", e);
        }
    }

    /**
     * 入队并检测失败：Redisson {@code RDelayedQueue.offer} 返回 void（不同于 JDK
     * {@code BlockingQueue.offer} 返回 boolean），无法用返回值判定，改用异常检测——
     * Redis 不可用等故障会抛 RuntimeException。失败记日志，靠 DB 兜底扫描救回。
     *
     * @return true 入队成功；false 入队失败（已记日志）
     */
    private boolean offerOrLog(Integer postId, long delaySeconds, String scene) {
        try {
            delayed.offer(postId, delaySeconds, TimeUnit.SECONDS);
            return true;
        } catch (Exception e) {
            log.warn("{} 入队失败 postId={} delay={}s，将依赖 DB 兜底", scene, postId, delaySeconds, e);
            return false;
        }
    }

    /**
     * 发布单篇，失败重试 MAX_RETRY 次。
     * publishScheduledPost 不抛异常即视为成功（含 affected=0：已被他处发布或条件不满足，
     * 幂等语义下等于"无需再发"）。仍抛异常则返回 false，由调用方决定回投或告警。
     */
    private boolean publishWithRetry(Integer postId) {
        for (int i = 1; i <= MAX_RETRY; i++) {
            try {
                postRepository.publishScheduledPost(postId);
                return true;
            } catch (Exception e) {
                log.warn("定时发布第 {}/{} 次失败 postId={}", i, MAX_RETRY, postId, e);
            }
        }
        return false;
    }

    @PreDestroy
    void shutdown() {
        // 1) 中断消费线程并等待收尾，避免 publish 执行到一半被强杀
        if (consumer != null) {
            consumer.interrupt();
            try {
                consumer.join(SHUTDOWN_JOIN_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        // 2) 释放 Redisson 延迟队列占用的资源
        try {
            if (delayed != null) {
                delayed.destroy();
            }
        } catch (Exception e) {
            log.warn("销毁延迟队列失败", e);
        }
    }
}
