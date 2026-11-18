package edu.szu.moments.posts;

public class PostFailure extends RuntimeException {
    private final int status;
    private final String code;

    public PostFailure(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public int status() { return status; }
    public String code() { return code; }

    public static PostFailure invalid(String message) {
        return new PostFailure(400, "VALIDATION_ERROR", message);
    }
    public static PostFailure unauthenticated() {
        return new PostFailure(401, "UNAUTHENTICATED", "请先登录");
    }
    public static PostFailure forbidden(String message) {
        return new PostFailure(403, "FORBIDDEN", message);
    }
    public static PostFailure missing() {
        return new PostFailure(404, "NOT_FOUND", "动态不存在或已不可见");
    }
    public static PostFailure conflict(String message) {
        return new PostFailure(409, "CONFLICT", message);
    }
    public static PostFailure dependency() {
        return new PostFailure(503, "DEPENDENCY_UNAVAILABLE", "关联服务暂不可用");
    }
}

