package edu.szu.moments.api;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
class ApiErrorHandler {
    private final M01Diagnostics diagnostics;
    ApiErrorHandler(M01Diagnostics diagnostics) { this.diagnostics = diagnostics; }
    @ExceptionHandler({DataAccessResourceFailureException.class, TransientDataAccessException.class,
            CannotCreateTransactionException.class})
    ResponseEntity<ApiResponse<Void>> handleDatabaseBusy(HttpServletRequest request) {
        diagnostics.reason(request, "database_global");
        return ResponseEntity.status(503).header("Retry-After", "1")
                .body(ApiResponse.error("SERVICE_BUSY", "服务繁忙，请稍后重试"));
    }

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
