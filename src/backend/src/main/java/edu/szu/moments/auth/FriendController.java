package edu.szu.moments.auth;

import com.fasterxml.jackson.databind.JsonNode;
import edu.szu.moments.api.ApiFailure;
import edu.szu.moments.api.ApiResponse;
import edu.szu.moments.api.PageData;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
class FriendController {
    record Decision(String action) {}
    private final FriendService friends;
    FriendController(FriendService friends) { this.friends=friends; }

    @GetMapping("/users/search")
    ApiResponse<PageData<FriendService.SearchUser>> search(@AuthenticationPrincipal UserProfile user,
            @RequestParam(defaultValue="") String keyword, @RequestParam(defaultValue="1") int page,
            @RequestParam(defaultValue="10") int pageSize) {
        return ApiResponse.ok(friends.search(user,keyword,page,pageSize));
    }
    @PostMapping("/friend-requests")
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<FriendService.RequestRow> send(@AuthenticationPrincipal UserProfile user, Authentication auth,
                                             @RequestBody JsonNode target) {
        JsonNode id = target.path("userId");
        if (!id.isIntegralNumber() || !id.canConvertToLong())
            throw new ApiFailure(HttpStatus.BAD_REQUEST,"VALIDATION_ERROR","userId 须为正整数");
        return ApiResponse.ok(friends.send(user,((Number)auth.getDetails()).intValue(),id.longValue()));
    }
    @GetMapping("/friend-requests")
    ApiResponse<PageData<FriendService.RequestRow>> requests(@AuthenticationPrincipal UserProfile user,
            @RequestParam(defaultValue="incoming") String direction, @RequestParam(defaultValue="1") int page,
            @RequestParam(defaultValue="10") int pageSize) {
        return ApiResponse.ok(friends.requests(user,direction,page,pageSize));
    }
    @PutMapping("/friend-requests/{id}")
    ApiResponse<FriendService.RequestRow> decide(@AuthenticationPrincipal UserProfile user, Authentication auth,
            @PathVariable long id, @RequestBody Decision decision) {
        return ApiResponse.ok(friends.decide(user,((Number)auth.getDetails()).intValue(),id,decision.action()));
    }
    @GetMapping("/friends")
    ApiResponse<PageData<UserProfile>> friends(@AuthenticationPrincipal UserProfile user,
            @RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="10") int pageSize) {
        return ApiResponse.ok(friends.friends(user,page,pageSize));
    }
    @DeleteMapping("/friends/{id}")
    ApiResponse<Void> remove(@AuthenticationPrincipal UserProfile user, Authentication auth, @PathVariable long id) {
        friends.remove(user,((Number)auth.getDetails()).intValue(),id);
        return ApiResponse.ok(null);
    }
}
