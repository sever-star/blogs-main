package com.syt.blog.DTO.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 修改用户角色请求体（管理员专用）
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UpdateUserRoleRequest {

    /** 目标角色：ADMIN=管理员，USER=普通用户 */
    @NotBlank(message = "角色不能为空")
    @Pattern(regexp = "ADMIN|USER", message = "角色只能是 ADMIN 或 USER")
    private String role;
}
