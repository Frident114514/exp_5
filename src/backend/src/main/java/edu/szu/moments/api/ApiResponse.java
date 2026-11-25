package edu.szu.moments.api;

public record ApiResponse<T>(String code, T data, String message) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>("OK", data, "");
    }

    public static ApiResponse<Void> error(String code, String message) {
        return new ApiResponse<>(code, null, message);
    }
}
