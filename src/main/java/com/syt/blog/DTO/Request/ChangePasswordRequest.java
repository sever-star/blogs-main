package com.syt.blog.DTO.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChangePasswordRequest {
    @NotBlank(message = "旧密码不能为空")
    @Size(min = 6, max = 32)
    private String oldPassword;
    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 32)
    private String newPassword;
}
