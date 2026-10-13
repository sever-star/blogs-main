package com.syt.blog.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 密码散列。
 * <p>
 * 这是本项目唯一用到的 Spring Security 组件：登录时由 {@code BlogUserService.login} 自己调
 * {@code BCryptPasswordEncoder.matches} 比对，<b>不走</b> Security 的
 * {@code DaoAuthenticationProvider}（理由见 {@link SecurityConfig} 的类注释）。
 * 传 {@code BCryptPasswordEncoder} 类型而非 {@code PasswordEncoder} 接口，
 * 是为了让调用方直接得到具体类型，避免依赖 Security 的自动装配。
 */
@Configuration
public class SecurityBeanConfig {

    @Bean
    public BCryptPasswordEncoder bCryptPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
