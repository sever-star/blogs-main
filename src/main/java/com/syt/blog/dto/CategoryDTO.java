package com.syt.blog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CategoryDTO {

    @NotBlank(message = "分类名称不能为空")
    @Size(min = 1, max = 20, message = "分类名称长度在1到20个字符之间")
    private String name;
    private Integer parentId;
    private Integer sortOrder;
}
