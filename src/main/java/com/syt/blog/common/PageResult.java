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
    private long total;// 总记录数
    private int page;// 当前页码
    private int pageSize;// 每页大小
    private int totalPages;// 总页数
}
