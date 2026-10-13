package com.syt.blog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * 排除 {@code UserDetailsServiceAutoConfiguration}：本项目没有用户存储走 Spring Security 的
 * 认证流程（登录自比对 BCrypt，认证靠 JWT），该自动装配只会造出一个内存用户并往日志里
 * 打印「Using generated security password」，纯属误导。
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableJpaAuditing
@EnableJpaRepositories(basePackages = "com.syt.blog.repository")
public class BlogApplication {

    public static void main(String[] args) {
        SpringApplication.run(BlogApplication.class, args);
    }

}
