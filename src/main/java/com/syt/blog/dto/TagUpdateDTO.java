package com.syt.blog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TagUpdateDTO {
    @NotBlank(message = "标签名称不能为空")
    @Size(min = 1,max=20,message = "标签名称长度在1到20个字符之间")
    private String name;
}
