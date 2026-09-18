package com.example.meetroom.interfaces.dto;

/**
 * 对外返回的错误响应，不包含 SQL、凭据或异常堆栈。
 * @param code 稳定错误码
 * @param message 不含内部敏感信息的错误说明
 */
public record ApiError(String code, String message) {
}
