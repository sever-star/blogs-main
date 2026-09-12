package com.syt.blog.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syt.blog.Vo.LoginResponse;
import com.syt.blog.Vo.UserVO;
import com.syt.blog.common.BusinessException;
import com.syt.blog.common.ErrorCode;
import com.syt.blog.common.GlobalExceptionHandler;
import com.syt.blog.dto.LoginDTO;
import com.syt.blog.dto.RegisterDTO;
import com.syt.blog.dto.UserDTO;
import com.syt.blog.entity.RefreshToken;
import com.syt.blog.service.BlogUserService;
import com.syt.blog.service.RefreshTokenService;
import com.syt.blog.util.JwtUtils;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.hamcrest.Matchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BlogUserController 单元测试。
 * <p>
 * Spring Boot 4.x 移除了 {@code @WebMvcTest} 与 {@code @MockBean}，故改用 Spring Framework 7
 * 原生的 {@link MockMvcBuilders#standaloneSetup} 构造 MockMvc：手动注入目标 Controller、
 * {@link GlobalExceptionHandler}（使 @Valid 校验失败与 BusinessException 被正确翻译）以及
 * 一个 Hibernate Validator（触发 @NotBlank/@Size/@Email）。三个协作者以 Mockito {@code @Mock} 注入。
 * <p>
 * 覆盖维度：路由绑定、@Valid 校验、@RequestHeader/@CookieValue 绑定、Controller 自身分支
 * （refresh / logout 的空值守护与 try-catch）、Cookie 读写、响应序列化、异常翻译。
 */
@ExtendWith(MockitoExtension.class)
class BlogUserControllerTest {

    private static final long REFRESH_EXPIRATION_MS = 604_800_000L; // 7 天，毫秒

    @Mock
    private BlogUserService blogUserService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private JwtUtils jwtUtils;

    private MockMvc mockMvc;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setup() {
        // 构造 Controller 实例并手动注入 @Value 字段（standalone 下无属性解析）
        BlogUserController controller =
                new BlogUserController(blogUserService, refreshTokenService, jwtUtils);
        ReflectionTestUtils.setField(controller, "refreshExpiration", REFRESH_EXPIRATION_MS);

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    // ===================== 测试数据工厂 =====================

    private UserVO userVO() {
        UserVO vo = new UserVO();
        vo.setId(1);
        vo.setUsername("testuser");
        vo.setNickname("测试昵称");
        vo.setEmail("test@example.com");
        return vo;
    }

    private LoginResponse loginResponse() {
        return new LoginResponse("access-token", userVO());
    }

    private String json(Object o) throws Exception {
        return objectMapper.writeValueAsString(o);
    }

    // ============================ 登录 ============================

    @Test
    void login_success() throws Exception {
        when(blogUserService.login(any(LoginDTO.class), any(), any())).thenReturn(loginResponse());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginDTO("testuser", "password123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.user.username").value("testuser"));

        verify(blogUserService).login(any(LoginDTO.class), any(), any());
    }

    @Test
    void login_usernameBlank_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginDTO("", "password123"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message", Matchers.containsString("用户名不能为空")));
    }

    @Test
    void login_usernameTooShort_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginDTO("ab", "password123"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("用户名长度在3到20个字符之间"));
    }

    @Test
    void login_usernameTooLong_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginDTO("a".repeat(21), "password123"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("用户名长度在3到20个字符之间"));
    }

    @Test
    void login_passwordBlank_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginDTO("testuser", ""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message", Matchers.containsString("密码不能为空")));
    }

    @Test
    void login_passwordTooShort_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginDTO("testuser", "12345"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("密码长度在6到32个字符之间"));
    }

    @Test
    void login_passwordTooLong_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginDTO("testuser", "p".repeat(33)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("密码长度在6到32个字符之间"));
    }

    @Test
    void login_wrongCredentials_returns401() throws Exception {
        when(blogUserService.login(any(LoginDTO.class), any(), any()))
                .thenThrow(new BusinessException(401, "用户名或密码错误"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginDTO("testuser", "wrongpwd"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("用户名或密码错误"));
    }

    // ============================ 获取当前用户 ============================

    @Test
    void me_success() throws Exception {
        when(blogUserService.getUser("Bearer test-token")).thenReturn(userVO());

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.username").value("testuser"))
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    void me_missingAuthHeader_returnsError() throws Exception {
        // 缺少必填请求头 -> MissingRequestHeaderException，被全局兜底处理器转为 500
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("服务器内部错误"));

        verify(blogUserService, never()).getUser(anyString());
    }

    // ============================ 更新用户信息 ============================

    @Test
    void update_success() throws Exception {
        UserVO updated = userVO();
        updated.setNickname("新昵称");
        updated.setWebsite("https://blog.example.com");
        when(blogUserService.update(eq("Bearer test-token"), any(UserDTO.class))).thenReturn(updated);

        UserDTO dto = new UserDTO();
        dto.setNickname("新昵称");
        dto.setWebsite("https://blog.example.com");

        mockMvc.perform(put("/api/auth/me")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.nickname").value("新昵称"))
                .andExpect(jsonPath("$.data.website").value("https://blog.example.com"));
    }

    @Test
    void update_missingAuthHeader_returnsError() throws Exception {
        UserDTO dto = new UserDTO();
        dto.setNickname("新昵称");

        mockMvc.perform(put("/api/auth/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(dto)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(500));

        verify(blogUserService, never()).update(anyString(), any(UserDTO.class));
    }

    @Test
    void update_userNotFound_returns401() throws Exception {
        when(blogUserService.update(eq("Bearer test-token"), any(UserDTO.class)))
                .thenThrow(new BusinessException(ErrorCode.AUTH_FAILED, "更新失败"));

        UserDTO dto = new UserDTO();
        dto.setNickname("新昵称");

        // 业务异常 AUTH_FAILED(401004)：HTTP 状态映射为 401，完整业务码保留在 $.code
        mockMvc.perform(put("/api/auth/me")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(dto)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401004))
                .andExpect(jsonPath("$.message").value("更新失败"));
    }

    // ============================ 注册 ============================

    @Test
    void register_success() throws Exception {
        when(blogUserService.register(any(RegisterDTO.class))).thenReturn(loginResponse());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RegisterDTO("newuser", "password123", "new@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.user.username").value("testuser"));
    }

    @Test
    void register_usernameBlank_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RegisterDTO("", "password123", "new@example.com"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message", Matchers.containsString("用户名不能为空")));
    }

    @Test
    void register_usernameTooShort_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RegisterDTO("ab", "password123", "new@example.com"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("用户名长度在3到20个字符之间"));
    }

    @Test
    void register_passwordBlank_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RegisterDTO("newuser", "", "new@example.com"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message", Matchers.containsString("密码不能为空")));
    }

    @Test
    void register_passwordTooShort_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RegisterDTO("newuser", "12345", "new@example.com"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("密码长度在6到32个字符之间"));
    }

    @Test
    void register_emailBlank_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RegisterDTO("newuser", "password123", ""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("邮箱不能为空"));
    }

    @Test
    void register_invalidEmail_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RegisterDTO("newuser", "password123", "not-an-email"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("邮箱格式不正确"));
    }

    @Test
    void register_userExists_returns401() throws Exception {
        when(blogUserService.register(any(RegisterDTO.class)))
                .thenThrow(new BusinessException(ErrorCode.AUTH_FAILED, "用户已存在"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RegisterDTO("testuser", "password123", "test@example.com"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401004))
                .andExpect(jsonPath("$.message").value("用户已存在"));
    }

    // ============================ 刷新令牌 ============================

    @Test
    void refresh_success() throws Exception {
        String oldRefresh = "old-refresh-token";
        RefreshToken rt = new RefreshToken();
        when(refreshTokenService.verifyRefreshToken(oldRefresh)).thenReturn(rt);
        when(jwtUtils.getUserIdFromToken(oldRefresh)).thenReturn(1);
        when(jwtUtils.getUsernameFromToken(oldRefresh)).thenReturn("testuser");
        when(jwtUtils.generateToken(1, "testuser")).thenReturn("new-access-token");
        when(jwtUtils.generateRefreshToken(1, "testuser")).thenReturn("new-refresh-token");

        mockMvc.perform(post("/api/auth/refresh").cookie(new Cookie("refreshToken", oldRefresh)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.accessToken").value("new-access-token"))
                // 新 refresh token 写回 Cookie
                .andExpect(cookie().value("refreshToken", "new-refresh-token"))
                .andExpect(cookie().httpOnly("refreshToken", true))
                .andExpect(cookie().path("refreshToken", "/api/auth"));

        verify(refreshTokenService).verifyRefreshToken(oldRefresh);
        verify(refreshTokenService).revokeRefreshToken(oldRefresh);
        verify(refreshTokenService).saveRefreshToken(eq(1), eq("new-refresh-token"), any(), any(), any());
    }

    @Test
    void refresh_noCookie_returns401001() throws Exception {
        mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isOk()) // Controller 直接返回 Result.error，HTTP 仍为 200
                .andExpect(jsonPath("$.code").value(ErrorCode.TOKEN_EXPIRED))
                .andExpect(jsonPath("$.message").value("Refresh token 不存在，请重新登录"));

        verify(refreshTokenService, never()).verifyRefreshToken(anyString());
    }

    @Test
    void refresh_emptyCookie_returns401001() throws Exception {
        mockMvc.perform(post("/api/auth/refresh").cookie(new Cookie("refreshToken", "")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.TOKEN_EXPIRED))
                .andExpect(jsonPath("$.message").value("Refresh token 不存在，请重新登录"));

        verify(refreshTokenService, never()).verifyRefreshToken(anyString());
    }

    @Test
    void refresh_invalidToken_returns401001_andClearsCookie() throws Exception {
        String badRefresh = "bad-refresh-token";
        when(refreshTokenService.verifyRefreshToken(badRefresh))
                .thenThrow(new RuntimeException("令牌已失效"));

        mockMvc.perform(post("/api/auth/refresh").cookie(new Cookie("refreshToken", badRefresh)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.TOKEN_EXPIRED))
                .andExpect(jsonPath("$.message").value("令牌已失效"))
                // 异常分支清除 Cookie
                .andExpect(cookie().maxAge("refreshToken", 0));

        verify(refreshTokenService, never()).revokeRefreshToken(anyString());
    }

    // ============================ 登出 ============================
    // logout 端点只做“透传”：把 refreshToken 委托给 BlogUserService.logout 处理
    // （吊销令牌 + 清除 Cookie 均发生在 Service 层，这里以 @Mock 注入，故不验证 Cookie）。

    @Test
    void logout_withCookie_delegatesToService() throws Exception {
        mockMvc.perform(post("/api/auth/logout").cookie(new Cookie("refreshToken", "rt")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("退出登录成功"));

        // Controller 把 refreshToken 透传给 service，由 service 负责吊销与清 Cookie
        verify(blogUserService).logout(eq("rt"), any());
    }

    @Test
    void logout_noCookie_stillReturnsSuccess() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("退出登录成功"));

        // 无 cookie 时 refreshToken 绑定为 null，仍委托 service（service 内部守护空值）
        verify(blogUserService).logout(isNull(), any());
    }

    @Test
    void logout_emptyCookie_delegatesToService() throws Exception {
        mockMvc.perform(post("/api/auth/logout").cookie(new Cookie("refreshToken", "")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("退出登录成功"));

        // 空字符串 cookie 绑定为 ""，透传给 service
        verify(blogUserService).logout(eq(""), any());
    }
}
