package edu.szu.moments.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.szu.moments.api.ApiFailure;
import edu.szu.moments.api.ApiResponse;
import edu.szu.moments.api.M01Diagnostics;
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
    private final M01Diagnostics diagnostics;

    AuthLoadFilter(AuthAdmission admission, ObjectMapper mapper) {
        this(admission, mapper, new M01Diagnostics(false));
    }

    AuthLoadFilter(AuthAdmission admission, ObjectMapper mapper, M01Diagnostics diagnostics) {
        this.admission = admission;
        this.mapper = mapper;
        this.diagnostics = diagnostics;
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
        var observation = diagnostics.begin(request);
        boolean admitted = false;
        boolean aborted = true;
        try (var permit = admission.admit(passwordPath(request.getServletPath()))) {
            admitted = true;
            diagnostics.admitted(observation);
            try { chain.doFilter(request, response); }
            finally { diagnostics.released(observation); }
            aborted = false;
        } catch (ApiFailure failure) {
            if (!admitted && failure.status().value() == 503) diagnostics.reason(request, "admission_rejected");
            reject(response, failure.status().value(), failure.code(), failure.getMessage());
            aborted = false;
        } catch (DataAccessResourceFailureException | TransientDataAccessException | CannotCreateTransactionException failure) {
            diagnostics.reason(request, "database_filter");
            reject(response, 503, "SERVICE_BUSY", "服务繁忙，请稍后重试");
            aborted = false;
        } catch (IOException | ServletException | RuntimeException failure) {
            aborted = true;
            throw failure;
        } catch (Exception failure) {
            aborted = true;
            throw new ServletException(failure);
        } finally {
            diagnostics.finish(observation, response.getStatus(), aborted);
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
