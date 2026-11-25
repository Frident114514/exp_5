package edu.szu.moments.auth;

import edu.szu.moments.api.ApiResponse;
import edu.szu.moments.api.PageData;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/users")
class AdminUserController {
    private final AdminUserService users;

    AdminUserController(AdminUserService users) { this.users = users; }

    @GetMapping
    ApiResponse<PageData<AdminUserService.UserRow>> list(@RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResponse.ok(users.list(keyword, page, pageSize));
    }

    @DeleteMapping("/{userId}")
    ApiResponse<Void> disable(@PathVariable long userId, @AuthenticationPrincipal UserProfile admin,
                              Authentication authentication) {
        users.disable(admin.id(), ((Number) authentication.getDetails()).intValue(), userId);
        return ApiResponse.ok(null);
    }
}
