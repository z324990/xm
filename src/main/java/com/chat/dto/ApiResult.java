package com.chat.dto;

public class ApiResult<T> {
    private int code;
    private String message;
    private T data;

    public ApiResult() {}

    public static <T> ApiResult<T> success(T data) {
        ApiResult<T> result = new ApiResult<>();
        result.code = 200;
        result.message = "success";
        result.data = data;
        return result;
    }

    public static <T> ApiResult<T> error(int code, String message) {
        ApiResult<T> result = new ApiResult<>();
        result.code = code;
        result.message = message;
        return result;
    }

    public int getCode() { return code; }
    public void setCode(int code) { this.code = code; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public T getData() { return data; }
    public void setData(T data) { this.data = data; }
}
