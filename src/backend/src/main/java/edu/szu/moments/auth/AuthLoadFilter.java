package edu.szu.moments.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.szu.moments.api.ApiFailure;
import edu.szu.moments.api.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Admission runs before JWT decoding, JSON binding, password hashing and database access. */
final class AuthLoadFilter extends OncePerRequestFilter {
    private final AuthAdmission admission;
    private final ObjectMapper mapper;

    AuthLoadFilter(AuthAdmission admission, ObjectMapper mapper) {
        this.admission = admission;
        this.mapper = mapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return !(request.getMethod().equals("POST") && passwordPath(path)
                || request.getMethod().equals("GET") && path.equals("/api/v1/users/me"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try (var permit = admission.admit(passwordPath(request.getServletPath()))) {
            chain.doFilter(request, response);
        } catch (ApiFailure failure) {
            reject(response, failure.status().value(), failure.code(), failure.getMessage());
        } catch (DataAccessResourceFailureException | TransientDataAccessException | CannotCreateTransactionException failure) {
            reject(response, 503, "SERVICE_BUSY", "服务繁忙，请稍后重试");
        } catch (IOException | ServletException | RuntimeException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new ServletException(failure);
        }
    }

    private static boolean passwordPath(String path) {
        // A trailing slash must not bypass admission before a later MVC mapping change.
        return path.equals("/api/v1/auth/login") || path.equals("/api/v1/auth/register")
                || path.equals("/api/v1/auth/login/") || path.equals("/api/v1/auth/register/");
    }

    private void reject(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setHeader("Retry-After", "1");
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        mapper.writeValue(response.getOutputStream(), ApiResponse.error(code, message));
    }
}
