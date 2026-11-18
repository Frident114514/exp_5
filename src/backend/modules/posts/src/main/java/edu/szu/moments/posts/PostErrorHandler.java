package edu.szu.moments.posts;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(basePackageClasses = PostController.class)
public class PostErrorHandler {
    private static final Logger LOG = LoggerFactory.getLogger(PostErrorHandler.class);

    @ExceptionHandler(PostFailure.class)
    ResponseEntity<ApiResponse<Void>> business(PostFailure failure) {
        return ResponseEntity.status(failure.status())
            .body(new ApiResponse<>(failure.code(), null, failure.getMessage()));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ApiResponse<Void>> input(Exception ignored) {
        return business(PostFailure.invalid("请求格式或参数类型不正确"));
    }

    @ExceptionHandler({DataIntegrityViolationException.class, PessimisticLockingFailureException.class})
    ResponseEntity<ApiResponse<Void>> conflict(Exception ignored) {
        return business(PostFailure.conflict("数据状态冲突，请刷新后重试"));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Void>> unexpected(Exception failure) {
        LOG.error("动态接口发生未预期异常", failure);
        return ResponseEntity.internalServerError()
            .body(new ApiResponse<>("INTERNAL_ERROR", null, "服务暂时不可用"));
    }
}

