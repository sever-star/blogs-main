package com.syt.blog.service;

import com.syt.blog.DTO.Mapper.UserMapper;
import com.syt.blog.DTO.Request.ChangePasswordRequest;
import com.syt.blog.DTO.Response.LoginResponse;
import com.syt.blog.common.BusinessException;
import com.syt.blog.common.ErrorCode;
import com.syt.blog.common.PageResult;
import com.syt.blog.common.ResourceNotFoundException;
import com.syt.blog.jooq.tables.daos.BlogUsersDao;
import com.syt.blog.jooq.tables.pojos.BlogUsers;
import com.syt.blog.repository.UserRepository;
import com.syt.blog.util.JwtUtils;
import com.syt.blog.util.SecurityUtils;
import com.syt.blog.DTO.Response.UserResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.sql.SQLDataException;
import java.util.List;
import java.util.Map;


@Service
@RequiredArgsConstructor
@Slf4j
public class BlogUserService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder bCryptPasswordEncoder;
    private final JwtUtils jwtUtils;
    private final RefreshTokenService refreshTokenService;
    private final BlogUsersDao blogUsersDao;

    @Value("${jwt.refresh-expiration}")
    private long refreshExpiration;

    /** blog_users.status = 1 表示正常，其余为禁用（与 JwtAuthenticationFilter 中的取值保持一致） */
    private static final short STATUS_ACTIVE = 1;

    /**
     * 用户登录
     *
     * @param blogUsers 登录参数
     * @param request HTTP 请求
     * @return 登录结果
     */
    public LoginResponse login(BlogUsers blogUsers, HttpServletRequest request, HttpServletResponse response) {

        BlogUsers users = userRepository.findByUsername(blogUsers.getUsername());
        if (users == null || !bCryptPasswordEncoder.matches(blogUsers.getPassword(), users.getPassword())) {
            throw new BusinessException(401, "用户名或密码错误");
        }

        // 禁用账号必须在密码校验**之后**单独抛 403：并进上面那行会被报成「用户名或密码错误」，
        // 前端无法区分「密码输错」和「账号被封」。此时密码已验证通过，泄露禁用状态是可接受的。
        // 403002 经 GlobalExceptionHandler.toHttpStatus 映射为 HTTP 403（见 docs/security-migration.md §9）。
        // 注意：这里挡住的是「新发凭证」；已签发的 token 由 JwtAuthenticationFilter 拦（P2-3 两半）。
        if (users.getStatus() == null || users.getStatus() != STATUS_ACTIVE) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED, "账号已被禁用");
        }
        //禁用所有刷新令牌
        refreshTokenService.revokeAllByUserId(users.getId());
        Long tokenVersion = bumpTokenVersion(users.getId());
        String accessToken = jwtUtils.generateToken(users.getId(), users.getUsername(),tokenVersion);
        String refreshToken = jwtUtils.generateRefreshToken(users.getId(), users.getUsername());

        String userAgent = request.getHeader("User-Agent");
        String ip = getClientIp(request);
        refreshTokenService.saveRefreshToken(
                users.getId(), refreshToken, null, userAgent, ip
        );

        setRefreshTokenCookie(response, refreshToken, refreshExpiration);

        UserResponse userResponse = UserMapper.INSTANCE.BlogUsersToUserResponse(users);
        LoginResponse loginResponse = new LoginResponse(accessToken, userResponse);
        return loginResponse;
    }
    /**
     * 获取客户端 IP 地址
     *
     * @param request HTTP 请求
     * @return 客户端 IP 地址
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    /**
     * 获取当前登录用户信息
     *
     * @return 用户信息
     */
    public UserResponse getUser() {
        BlogUsers user = userRepository.findById(SecurityUtils.currentUserId());
        if (user == null) {
            throw new BusinessException(ErrorCode.AUTH_FAILED, "用户不存在");
        }
        return UserMapper.INSTANCE.BlogUsersToUserResponse(user);
    }

    /**
     * 用户注册
     *
     * @param blogUsers 注册参数
     * @return 注册结果
     */
    @Transactional
    public LoginResponse register(BlogUsers blogUsers) {
        String username = blogUsers.getUsername();
        log.info("blogUsers user: " + username);

        BlogUsers existingUser = userRepository.findByUsername(username);
        log.info("Existing user: " + existingUser);

        if (existingUser != null) {
            throw new BusinessException(ErrorCode.AUTH_FAILED, "用户已存在");
        }

        // role/status 的 DB 默认值在 insert 后不回填 pojo，这里显式赋值，
        // 保证随后组装的注册响应（UserMapper 全量映射）带上真实的角色与状态。
        blogUsers.setRole("USER");
        blogUsers.setStatus((short) STATUS_ACTIVE);
        blogUsers.setPassword(bCryptPasswordEncoder.encode(blogUsers.getPassword()));
        blogUsersDao.insert(blogUsers);

        log.info("userId: " + blogUsers.getId());
        Long tokenVersion = getTokenVersion(blogUsers.getId());
        String accessToken = jwtUtils.generateToken(blogUsers.getId(), blogUsers.getUsername(),tokenVersion);
        LoginResponse loginResponse = new LoginResponse();
        loginResponse.setAccessToken(accessToken);
        UserResponse userResponse = UserMapper.INSTANCE.BlogUsersToUserResponse(blogUsers);
        loginResponse.setUser(userResponse);
        return loginResponse;
    }

    /**
     * 更新用户信息
     *
     * @param patch 待更新的资料字段（null 表示该字段不改）
     * @return 更新后的用户信息
     */
    @Transactional
    public UserResponse update(BlogUsers patch) {
        // 以 SecurityContext 里的用户为准：token 里没有的目标 id 就取不到，天然防越权改他人资料
        BlogUsers existing = userRepository.findById(SecurityUtils.currentUserId());
        if (existing == null) {
            throw new BusinessException(ErrorCode.AUTH_FAILED, "用户不存在");
        }

        // 以 token 定位到的用户为准，防止越权修改他人信息，并保证主键正确。
        // 从 existing 出发而非直接更新 patch：patch 里只有请求带到的资料字段，
        // 其余列（password/status/role/last_login_at/时间戳）保持库中原值。
        // 另外 jOOQ 只省略「非空约束且为 null」的列，可空列会被写成 NULL，
        // 所以必须在这里做「传了才改」，否则没传的字段会被清空。
        if (patch.getNickname() != null) existing.setNickname(patch.getNickname());
        if (patch.getAvatar() != null)   existing.setAvatar(patch.getAvatar());
        if (patch.getBio() != null)      existing.setBio(patch.getBio());
        if (patch.getWebsite() != null)  existing.setWebsite(patch.getWebsite());
        if (patch.getGithub() != null)   existing.setGithub(patch.getGithub());
        if (patch.getWeibo() != null)    existing.setWeibo(patch.getWeibo());

        blogUsersDao.update(existing);
        return UserMapper.INSTANCE.BlogUsersToUserResponse(existing);
    }

    /**
     * 分页查询用户（管理员专用）
     *
     * @param keyword  可选，模糊匹配用户名/昵称/邮箱
     * @param page     页码（1 基）
     * @param pageSize 每页大小
     * @return 分页结果，data 为 {@link UserResponse} 列表
     */
    public PageResult getUsersPaged(String keyword, Integer page, Integer pageSize) {
        PageResult result = userRepository.findPaged(page, pageSize, keyword);
        // 必须转换成 UserResponse：PageResult.data 是 List<?>，直接回传 jOOQ POJO
        // 会把 password 哈希等敏感字段一并序列化给前端。
        @SuppressWarnings("unchecked")
        List<BlogUsers> rows = (List<BlogUsers>) result.getData();
        List<UserResponse> users = rows.stream()
                .map(UserMapper.INSTANCE::BlogUsersToUserResponse)
                .toList();
        return new PageResult(users, result.getTotal(), result.getPage(),
                result.getPageSize(), result.getTotalPages());
    }

    /**
     * 修改用户角色（管理员专用）
     *
     * @param id   目标用户 ID
     * @param role 目标角色：ADMIN / USER
     * @return 更新后的用户信息
     */
    @Transactional
    public UserResponse updateRole(Integer id, String role) {
        // 不许改自己：管理员若把自己降为普通用户，就没有入口再改回去
        if (id.equals(SecurityUtils.currentUserId())) {
            throw new BusinessException(400, "不能修改自己的角色");
        }
        BlogUsers existing = requireUser(id);

        // 降级最后一个「启用中」的管理员会导致后台无人可管
        if ("ADMIN".equals(existing.getRole()) && !"ADMIN".equals(role)
                && userRepository.countActiveAdmins() <= 1) {
            throw new BusinessException(400, "不能降级最后一个启用状态的管理员");
        }

        existing.setRole(role);
        blogUsersDao.update(existing);
        log.info("管理员 {} 将用户 {} 的角色改为 {}", SecurityUtils.currentUserId(), id, role);
        return UserMapper.INSTANCE.BlogUsersToUserResponse(existing);
    }

    /**
     * 启用/禁用用户（管理员专用）。JwtAuthenticationFilter 每请求查库，
     * 状态变更后该用户的存量 token 下一次请求即被拒绝（403002）。
     *
     * @param id     目标用户 ID
     * @param status 目标状态：1=正常，0=禁用
     * @return 更新后的用户信息
     */
    @Transactional
    public UserResponse updateStatus(Integer id, Integer status) {
        // 不许动自己的状态：避免管理员误把自己禁用后无人能恢复
        if (id.equals(SecurityUtils.currentUserId())) {
            throw new BusinessException(400, "不能修改自己的状态");
        }
        BlogUsers existing = requireUser(id);

        boolean disablingAdmin = status != STATUS_ACTIVE
                && "ADMIN".equals(existing.getRole())
                && (existing.getStatus() != null && existing.getStatus() == STATUS_ACTIVE);
        if (disablingAdmin && userRepository.countActiveAdmins() <= 1) {
            throw new BusinessException(400, "不能禁用最后一个启用状态的管理员");
        }

        existing.setStatus((short) (int) status);
        blogUsersDao.update(existing);
        log.info("管理员 {} 将用户 {} 的状态改为 {}", SecurityUtils.currentUserId(), id, status);
        return UserMapper.INSTANCE.BlogUsersToUserResponse(existing);
    }
    /***
     *
     * @param id 目标用户 ID
     * @return 重置后的密码
     */
    @Transactional
    public Map<String, String> resetPassword(Integer id) {
        BlogUsers existing =requireUser(id);
        existing.setPassword(bCryptPasswordEncoder.encode("123456"));
        blogUsersDao.update(existing);
        log.info("管理员 {} 将用户 {} 的密码重置为 {}", SecurityUtils.currentUserId(), id, "123456");
        return Map.of("message", "123456");
    }
    /** 取目标用户，不存在抛 404 */
    private BlogUsers requireUser(Integer id) {
        BlogUsers existing = userRepository.findById(id);
        if (existing == null) {
            throw new ResourceNotFoundException("用户不存在，ID: " + id);
        }
        return existing;
    }

    @Transactional
    public void logout(String refreshToken, HttpServletResponse response) {


        if (refreshToken != null && !refreshToken.isEmpty()) {
            try {
                Integer userId = jwtUtils.getUserIdFromRefreshToken(refreshToken);
                refreshTokenService.revokeRefreshToken(refreshToken);
                bumpTokenVersion(userId);
            }catch (Exception e) {
                log.debug("撤销刷新令牌失败", e);
            }

        }
        clearRefreshTokenCookie(response);

    }

    /**
     * 清除刷新令牌 cookie
     *
     * @param response HTTP 响应
     */
    private void clearRefreshTokenCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie("refreshToken", null);
        cookie.setHttpOnly(true);
        cookie.setSecure(false);
        cookie.setPath("/api/auth");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }

    /**
     * 设置刷新令牌 cookie
     *
     * @param response HTTP 响应
     * @param token    刷新令牌
     * @param maxAgeMs 刷新令牌有效期（毫秒）
     */
    private void setRefreshTokenCookie(HttpServletResponse response, String token, long maxAgeMs) {
        Cookie cookie = new Cookie("refreshToken", token);
        cookie.setHttpOnly(true);
        cookie.setSecure(false);
        cookie.setPath("/api/auth");
        cookie.setMaxAge((int) (maxAgeMs / 1000));
        response.addCookie(cookie);
    }

    @Transactional
    public void updatePassword(@Valid ChangePasswordRequest changePasswordRequest) {
        Integer userId = SecurityUtils.currentUserId();
        String oldPassword = changePasswordRequest.getOldPassword();
        String newPassword = changePasswordRequest.getNewPassword();
        BlogUsers existing = userRepository.findById(userId);
        if (!bCryptPasswordEncoder.matches(oldPassword, existing.getPassword())) {
            throw new BusinessException(400, "旧密码错误");
        }
        if (newPassword.equals(oldPassword)) {
            throw new BusinessException(400, "新密码不能与旧密码相同");
        }
        userRepository.updatePassword(userId, bCryptPasswordEncoder.encode(newPassword));
        log.info("用户 {} 更新密码为 {}", userId, newPassword);
        refreshTokenService.revokeAllByUserId(userId);
        //更新 token 版本
        bumpTokenVersion(userId);
    }

    public Long bumpTokenVersion(Integer userId) {
        return userRepository.bumpTokenVersion(userId);
    }

    public Long getTokenVersion(Integer userId) {
        return userRepository.getTokenVersion(userId);
    }
}