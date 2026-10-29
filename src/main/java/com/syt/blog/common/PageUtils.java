package com.syt.blog.common;

import java.util.List;

/**
 * 分页工具：统一 totalPages 计算与 PageResult 组装，避免各处复制「ceil + 构造」两行。
 * <p>
 * page 为 1 基；pageSize <= 0 时 totalPages 记 0，避免除零。
 * 用浮点取整，不复刻早期 {@code total / pageSize} 整数除法截断的问题。
 */
public final class PageUtils {
    private PageUtils() {
    }

    /** 总页数：pageSize <= 0 视作 0，避免除零。 */
    public static int totalPages(long total, int pageSize) {
        return pageSize > 0 ? (int) Math.ceil((double) total / pageSize) : 0;
    }

    /** 用当前页数据 + 总数组装 PageResult，自动算 totalPages（DB 层 limit/offset 查完后收尾用）。 */
    public static PageResult of(List<?> data, long total, int page, int pageSize) {
        return new PageResult(data, total, page, pageSize, totalPages(total, pageSize));
    }
}
