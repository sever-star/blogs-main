package com.syt.blog.config;

import com.syt.blog.util.JwtAccessDeniedHandler;
import com.syt.blog.util.JwtAuthenticationEntryPoint;
import com.syt.blog.util.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Spring Security 鉴权链。负责「授权」；「认证」由 {@link JwtAuthenticationFilter} 完成。
 * <p>
 * <b>分工</b>：不用 {@code UserDetailsService} / {@code DaoAuthenticationProvider} 做密码校验——
 * 那套东西在 JWT 场景下只在登录那一刻被调用一次，之后每个请求的认证都靠 token 完成。
 * 登录仍走 {@code BlogUserService.login} 的 BCrypt 比对，本类只决定「谁能访问什么」。
 * <p>
 * <b>为什么必须在这里配 CORS</b>：Security 过滤器链在 {@code DispatcherServlet} 之前，
 * 只配 MVC 层的 {@code addCorsMappings}，对被 Security 拦下的请求（含预检 OPTIONS）不生效，
 * 表现为「浏览器报 CORS 错误」——这正是 pom.xml 里那段历史注释担心的现象。
 * {@code http.cors()} 之后预检会被 {@code CorsFilter} 直接短路返回，走不到授权阶段。
 *
 * @see docs/security-migration.md 迁移方案与授权矩阵
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;

    /**
     * 允许的跨域来源。留 {@code *} 是为了保持迁移前行为；上线前建议按
     * {@code docs/auth-review.md} P2-4 收紧成白名单。
     */
    @Value("${blog.cors.allowed-origins:*}")
    private String[] allowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // CORS 必须先于授权，否则预检 OPTIONS 会被判为未认证
                /** 配置CORS **/
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // 无状态：认证凭证是 Authorization 头里的 JWT，不建 HttpSession
                /** 设置框架不使用session **/
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 认证凭证不在 Cookie 里，浏览器不会自动携带，CSRF 前提不成立。
                // refresh/logout 走 Cookie，靠 Cookie 的 SameSite=Lax 兜住（见 auth-review.md P2-2）
                /** 禁用csrf **/
                .csrf(csrf -> csrf.disable())
                // 下面三个必须关：启用会带来表单登录页、原生 Basic 弹框、以及 Security 自带的
                // POST /logout——后者与本项目的 /api/auth/logout 语义重叠
                /** 禁用表单登录 **/
                .formLogin(formLogin -> formLogin.disable())
                /** 禁用httpBasic **/
                .httpBasic(basic -> basic.disable())
                /** 禁用logout **/
                .logout(logout -> logout.disable())
                .exceptionHandling(ex -> ex
                        /** 设置认证失败和授权失败的处理程序 **/
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                        .accessDeniedHandler(jwtAccessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        // 404/500 会 forward 到 /error；不放行会导致错误响应被二次拦截成 401
                        .requestMatchers("/error").permitAll()

                        // ── 公开：认证端点（不带 token 才进得来）────────────────────
                        .requestMatchers(HttpMethod.POST, "/api/auth/login",
                                "/api/auth/register", "/api/auth/refresh",
                                "/api/auth/logout").permitAll()

                        // ── 待审列表仅管理员可见，必须在下面 /api/posts/** permitAll 之前 ──
                        .requestMatchers(HttpMethod.GET, "/api/posts/pending").hasAuthority("ADMIN")

                        // ── 公开：内容只读。写方法的规则声明在其后，两者方法不同不会互相匹配 ──
                        .requestMatchers(HttpMethod.GET, "/api/posts", "/api/posts/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/categories", "/api/categories/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/tags", "/api/tags/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/posts/*/comments").permitAll()

                        // ── 公开：AI 助手对游客开放，成本控制靠 AiRateLimiter 按 IP 限流 ──
                        // 会话归属由 resolveOwner 决定：登录归用户，未登录按 IP
                        .requestMatchers("/api/ai/**").permitAll()

                        // ── 需登录 ────────────────────────────────────────────────
                        .requestMatchers(HttpMethod.GET, "/api/auth/me").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/auth/me").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/upload/avatar").authenticated()
                        // 创建/编辑文章对所有登录用户开放（提交审核流程）；
                        // 「只有作者本人能编辑」由 BlogPostService.updatePost 校验
                        .requestMatchers(HttpMethod.POST, "/api/posts").authenticated()
                        // 注意：本条按前缀匹配 /api/posts/**，若日后新增 approve/reject
                        // 等管理子路径，必须在本条**之前**声明为 hasAuthority("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/posts/**").authenticated()
                        // 收藏/取消收藏登录即可。DELETE 必须在下面 ADMIN 的
                        // DELETE /api/posts/** 规则**之前**声明，否则取消收藏会被误判成管理员操作
                        .requestMatchers(HttpMethod.POST, "/api/posts/*/favorite").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/posts/*/favorite").authenticated()
                        // 点赞/取消点赞同上：DELETE 必须先于下面 ADMIN 的 /api/posts/** 规则
                        .requestMatchers(HttpMethod.POST, "/api/posts/*/like").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/posts/*/like").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/posts/**").authenticated()
                        //评论对所有登录用户开放
                        .requestMatchers(HttpMethod.POST, "/api/posts/*/comments").authenticated()
                        // ── 需博主/管理员 ──────────────────────────────────────────
                        // 用 hasAuthority 而非 hasRole：后者会自动加 ROLE_ 前缀，
                        // 而 blog_users.role 存的就是 ADMIN，用 hasRole 会永远拒绝
                        .requestMatchers(HttpMethod.POST, "/api/categories").hasAuthority("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/categories/**").hasAuthority("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/categories/**").hasAuthority("ADMIN")

                        .requestMatchers(HttpMethod.POST, "/api/tags").hasAuthority("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/tags/**").hasAuthority("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/tags/**").hasAuthority("ADMIN")

                        // 用户管理：GET 也拦——列表含邮箱等信息，不能放开给普通登录用户
                        .requestMatchers(HttpMethod.GET, "/api/users", "/api/users/**").hasAuthority("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/users/**").hasAuthority("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/users/*/reset-password").hasAuthority("ADMIN")

                        // 兜底：没被上面任何一条覆盖的路径一律要登录
                        .anyRequest().authenticated()
                )
                /** 添加JWT认证过滤器到Spring Security过滤器链 **/
                .addFilterBefore(jwtAuthenticationFilter, AuthorizationFilter.class);

        return http.build();
    }

    /**
     * CORS 配置源，Security 侧由 {@code http.cors()} 使用。
     * <p>
     * 这是全项目**唯一**的 CORS 配置点（原先 {@code WebConfig.addCorsMappings} 那份已删除），
     * 避免两处配置漂移。
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of(allowedOrigins));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}