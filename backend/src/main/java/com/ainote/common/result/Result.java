package com.ainote.common.result;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 统一返回结果封装
 *
 * @param <T> 数据类型
 */
@Data
public class Result<T> {

    @Schema(description = "状态码，200 成功")
    private int code;

    @Schema(description = "提示信息")
    private String message;

    @Schema(description = "返回数据")
    private T data;

    public static <T> Result<T> success() {
        return build(ResultCode.SUCCESS, null);
    }

    public static <T> Result<T> success(T data) {
        return build(ResultCode.SUCCESS, data);
    }

    public static <T> Result<T> success(String message, T data) {
        Result<T> r = build(ResultCode.SUCCESS, data);
        r.message = message;
        return r;
    }

    public static <T> Result<T> fail(ResultCode code) {
        return build(code, null);
    }

    public static <T> Result<T> fail(ResultCode code, String message) {
        Result<T> r = build(code, null);
        r.message = message;
        return r;
    }

    public static <T> Result<T> fail(int code, String message) {
        Result<T> r = new Result<>();
        r.code = code;
        r.message = message;
        return r;
    }

    private static <T> Result<T> build(ResultCode code, T data) {
        Result<T> r = new Result<>();
        r.code = code.getCode();
        r.message = code.getMessage();
        r.data = data;
        return r;
    }
}