package edu.szu.moments.auth;

/**
 * M01 认证模块的最小示例。
 *
 * <p>正式服务接入 Spring Security 和 BCrypt 后，可将本示例中的规则
 * 替换为真实的用户表、密码哈希和 JWT 校验。</p>
 */
public final class AuthDemo {
    private AuthDemo() {
    }

    public static boolean validCredentials(String username, String password) {
        return username != null
                && !username.isBlank()
                && password != null
                && password.length() >= 8;
    }

    public static boolean canAccessAdminApi(String role, boolean enabled) {
        return enabled && "ADMIN".equals(role);
    }

    public static String loginResult(String username, String password) {
        return validCredentials(username, password) ? "LOGIN_ACCEPTED" : "LOGIN_REJECTED";
    }
}
