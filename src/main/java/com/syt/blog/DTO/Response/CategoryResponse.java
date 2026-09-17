package com.syt.blog.DTO.Response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CategoryResponse {

    private Integer id;
    private String name;
    private Integer parentId;
    private Integer sortOrder;
    private LocalDateTime createdAt;
}
