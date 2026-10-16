package com.syt.blog.controller;

import com.syt.blog.DTO.Request.UpdateUserRoleRequest;
import com.syt.blog.DTO.Request.UpdateUserStatusRequest;
import com.syt.blog.DTO.Response.UserResponse;
import com.syt.blog.common.PageResult;
import com.syt.blog.common.Result;
import com.syt.blog.service.BlogUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 用户管理（仅管理员）：分页查用户、改角色、启用/禁用。
 * <p>
 * 与 {@link BlogUserController}（/api/auth，本人资料）分离；
 * 本控制器的所有路径（含 GET，列表含邮箱）由 SecurityConfig 限定
 * {@code hasAuthority("ADMIN")}。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class BlogUserManageController {

    private final BlogUserService blogUserService;

    /**
     * 分页获取用户列表，支持 keyword 模糊搜索（用户名/昵称/邮箱）。
     */
    @GetMapping
    public Result<PageResult> getUsersPaged(@RequestParam(required = false) String keyword,
                                            @RequestParam(defaultValue = "1") Integer page,
                                            @RequestParam(defaultValue = "10") Integer pageSize) {
        PageResult pageResult = blogUserService.getUsersPaged(keyword, page, pageSize);
        return Result.success(pageResult);
    }

    /**
     * 修改用户角色（ADMIN / USER）。
     */
    @PutMapping("/{id}/role")
    public Result<UserResponse> updateUserRole(@PathVariable Integer id,
                                               @Valid @RequestBody UpdateUserRoleRequest request) {
        UserResponse user = blogUserService.updateRole(id, request.getRole());
        return Result.success(user);
    }

    /**
     * 启用/禁用用户（status：1=正常，0=禁用）。
     */
    @PutMapping("/{id}/status")
    public Result<UserResponse> updateUserStatus(@PathVariable Integer id,
                                                 @Valid @RequestBody UpdateUserStatusRequest request) {
        UserResponse user = blogUserService.updateStatus(id, request.getStatus());
        return Result.success(user);
    }

    @PostMapping("/{id}/reset-password")
    public Result<Map<String, String>> resetPassword(@PathVariable Integer id) {
        Map<String,String> result = blogUserService.resetPassword(id);
        return Result.success(result);
    }
}
