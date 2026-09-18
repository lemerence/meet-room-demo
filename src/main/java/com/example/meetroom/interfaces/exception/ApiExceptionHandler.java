package com.example.meetroom.interfaces.exception;

import com.example.meetroom.interfaces.dto.ApiError;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 将数据库访问异常转换为安全、明确的 HTTP 错误响应。 */
@RestControllerAdvice
public class ApiExceptionHandler {

    /** 将数据库异常映射为 503，避免把失败伪装成空列表或泄露内部信息。 */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiError> handleDatabaseFailure(DataAccessException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiError("DATABASE_UNAVAILABLE", "数据库暂时不可用，请稍后重试"));
    }
}
