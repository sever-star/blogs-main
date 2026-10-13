package com.syt.blog.DTO.Request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 启用/禁用用户请求体（管理员专用）
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UpdateUserStatusRequest {

    /** 目标状态：1=正常，0=禁用 */
    @NotNull(message = "状态不能为空")
    @Min(value = 0, message = "状态只能是 0 或 1")
    @Max(value = 1, message = "状态只能是 0 或 1")
    private Integer status;
}
