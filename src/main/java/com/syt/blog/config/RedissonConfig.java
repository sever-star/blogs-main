package com.syt.blog.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Redisson 客户端配置。
 * <p>
 * 不使用 redisson-spring-boot-starter 的 autoconfigure——它内部声明
 * {@code @EnableConfigurationProperties(RedisProperties.class)}，引用的
 * {@code org.springframework.boot.autoconfigure.data.redis.RedisProperties} 在
 * Spring Boot 4 已随 data-redis autoconfigure 重构被移走，导致启动期
 * {@link ClassNotFoundException}。这里改用纯 {@code redisson} artifact，
 * 手动从 {@code spring.data.redis.*} 读取连接信息构造 {@link RedissonClient}，
 * 与 {@code StringRedisTemplate} 共用同一份 yaml 配置。
 * <p>
 * {@code destroyMethod = "shutdown"}：容器关闭时调用 {@link RedissonClient#shutdown()}
 * 释放连接池，与 {@code ScheduledPostDispatcher.shutdown()} 里 {@code delayed.destroy()} 配合收尾。
 */
@Configuration
public class RedissonConfig {

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient(
            @Value("${spring.data.redis.host}") String host,
            @Value("${spring.data.redis.port}") int port,
            @Value("${spring.data.redis.password:}") String password,
            @Value("${spring.data.redis.timeout:2s}") Duration timeout) {
        Config config = new Config();
        var single = config.useSingleServer()
                .setAddress("redis://" + host + ":" + port)
                .setConnectTimeout((int) timeout.toMillis())
                .setTimeout((int) timeout.toMillis());
        if (password != null && !password.isBlank()) {
            single.setPassword(password);
        }
        return Redisson.create(config);
    }
}
