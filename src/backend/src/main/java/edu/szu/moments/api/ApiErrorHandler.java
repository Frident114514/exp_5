package edu.szu.moments.api;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class ApiErrorHandler {
    @ExceptionHandler(ApiFailure.class)
    ResponseEntity<ApiResponse<Void>> handleFailure(ApiFailure failure) {
        return ResponseEntity.status(failure.status())
                .body(ApiResponse.error(failure.code(), failure.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiResponse<Void>> handleUnreadableJson() {
        return ResponseEntity.badRequest().body(ApiResponse.error("VALIDATION_ERROR", "请求体须为有效的 JSON 对象"));
    }
}
