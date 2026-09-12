package com.syt.blog.common;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PageResult {
    private List<?> data;
    private long total;
    private int page;
    private int pageSize;
    private int totalPages;
}
