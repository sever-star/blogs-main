package com.syt.blog.Vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TagResponse {
    private Integer id;
    private String name;
    private LocalDateTime createdAt;
}
