package edu.szu.moments.media;

import edu.szu.moments.posts.ApiResponse;
import edu.szu.moments.posts.PostFailure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice(basePackageClasses = MediaController.class)
public class MediaErrorHandler {
    private static final Logger LOG = LoggerFactory.getLogger(MediaErrorHandler.class);

    @ExceptionHandler(MediaFailure.class)
    ResponseEntity<ApiResponse<Void>> mediaFailure(MediaFailure failure) {
        HttpHeaders headers = new HttpHeaders();
        if (failure.status() == 416 && failure.totalSize() != null) {
            headers.add(HttpHeaders.ACCEPT_RANGES, "bytes");
            headers.add(HttpHeaders.CONTENT_RANGE, "bytes */" + failure.totalSize());
        }
        return ResponseEntity.status(failure.status())
            .headers(headers)
            .body(new ApiResponse<>(failure.code(), null, failure.getMessage()));
    }

    @ExceptionHandler(PostFailure.class)
    ResponseEntity<ApiResponse<Void>> postFailure(PostFailure failure) {
        return ResponseEntity.status(failure.status())
            .body(new ApiResponse<>(failure.code(), null, failure.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiResponse<Void>> uploadTooLarge(MaxUploadSizeExceededException ignored) {
        return mediaFailure(MediaFailure.fileTooLarge("上传文件超过大小限制"));
    }

    @ExceptionHandler({MissingServletRequestPartException.class, MultipartException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ApiResponse<Void>> badInput(Exception ignored) {
        return mediaFailure(MediaFailure.invalidFile("请求格式或上传文件参数不正确"));
    }

    @ExceptionHandler({DataIntegrityViolationException.class, PessimisticLockingFailureException.class})
    ResponseEntity<ApiResponse<Void>> conflict(Exception ignored) {
        return postFailure(PostFailure.conflict("数据状态冲突，请刷新后重试"));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Void>> unexpected(Exception failure) {
        LOG.error("媒体接口发生未预期异常", failure);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new ApiResponse<>("INTERNAL_ERROR", null, "服务暂时不可用"));
    }
}
