package com.greenhouse.common;

import lombok.Data;

/**
 * 统一 API 响应格式
 * 所有接口都返回这个格式：{ "code": 200, "message": "成功", "data": {...} }
 */
@Data
public class Result<T> {

    private int code;       // 状态码：200 成功，其他失败
    private String message; // 提示信息
    private T data;         // 返回的数据

    private Result() {}

    // ---- 工厂方法：快速构建返回结果 ----

    public static <T> Result<T> ok(T data) {
        Result<T> r = new Result<>();
        r.code = 200;
        r.message = "成功";
        r.data = data;
        return r;
    }

    public static <T> Result<T> ok() {
        return ok(null);
    }

    public static <T> Result<T> fail(String message) {
        Result<T> r = new Result<>();
        r.code = 500;
        r.message = message;
        return r;
    }

    public static <T> Result<T> fail(int code, String message) {
        Result<T> r = new Result<>();
        r.code = code;
        r.message = message;
        return r;
    }
}
