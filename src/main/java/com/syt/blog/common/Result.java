package com.syt.blog.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一响应结果封装类
 *
 * @param <T> 响应数据类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {


    /** 状态码：200 表示成功，其他表示异常 */
    private int code;

    /** 提示信息 */
    private String message;

    /** 响应数据 */
    private T data;

    // ==================== 成功响应 ====================

    /**
     * 返回带数据的成功响应
     *
     * @param data 响应数据
     * @param <T>  数据类型
     * @return Result 对象
     */
    public static <T> Result<T> success(T data) {
        return new Result<>(200, "操作成功", data);
    }

    /**
     * 返回无数据的成功响应
     *
     * @param <T> 数据类型
     * @return Result 对象
     */
    public static <T> Result<T> success() {
        return new Result<>(200, "操作成功", null);
    }

    // ==================== 失败响应 ====================

    /**
     * 返回自定义状态码和错误信息
     *
     * @param code    状态码
     * @param message 错误信息
     * @param <T>     数据类型
     * @return Result 对象
     */
    public static <T> Result<T> error(int code, String message) {
        return new Result<>(code, message, null);
    }

    /**
     * 返回默认 500 错误响应
     *
     * @param message 错误信息
     * @param <T>     数据类型
     * @return Result 对象
     */
    public static <T> Result<T> error(String message) {
        return new Result<>(500, message, null);
    }
}
