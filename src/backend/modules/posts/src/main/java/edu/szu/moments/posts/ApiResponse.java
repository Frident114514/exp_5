package edu.szu.moments.posts;

/** 与全组接口约定一致；M01 整合公共类型时可替换，JSON 形状不变。 */
public record ApiResponse<T>(String code, T data, String message) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>("OK", data, "");
    }
}

