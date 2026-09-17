package com.syt.blog.DTO.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TagRequest {
    @NotBlank(message = "标签名称不能为空")
    @Size(min = 1,max=20,message = "标签名称长度在1到20个字符之间")
    private String name;
    private LocalDateTime createdAt;
}
