package edu.szu.moments.posts;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.*;

import static edu.szu.moments.posts.PostModels.*;

@RestController
@RequestMapping("/api/v1")
public class PostController {
    private final PostService service;
    private final PostPayloadParser parser;

    public PostController(PostService service, PostPayloadParser parser) {
        this.service = service;
        this.parser = parser;
    }

    @PostMapping("/posts")
    public ApiResponse<CreatedPost> create(@RequestBody JsonNode body) {
        return ApiResponse.ok(service.create(parser.parse(body, false)));
    }

    @PatchMapping("/posts/{postId}")
    public ApiResponse<PostView> edit(@PathVariable long postId, @RequestBody JsonNode body) {
        return ApiResponse.ok(service.edit(postId, parser.parse(body, true)));
    }

    @DeleteMapping("/posts/{postId}")
    public ApiResponse<Void> delete(@PathVariable long postId) {
        service.deleteOwn(postId);
        return ApiResponse.ok(null);
    }

    @GetMapping("/posts/{postId}")
    public ApiResponse<PostView> detail(@PathVariable long postId) {
        return ApiResponse.ok(service.detail(postId));
    }

    @GetMapping("/posts")
    public ApiResponse<PageView<PostView>> list(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String tag,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResponse.ok(service.publicList(PostFilter.parse(from, to, tag, null, null, page, pageSize)));
    }

    @GetMapping("/users/me/posts")
    public ApiResponse<PageView<PostView>> mine(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResponse.ok(service.mine(page, pageSize));
    }

    @GetMapping("/admin/posts")
    public ApiResponse<PageView<PostView>> adminList(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long authorId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResponse.ok(service.adminList(PostFilter.parse(from, to, tag, keyword, authorId, page, pageSize)));
    }

    @DeleteMapping("/admin/posts/{postId}")
    public ApiResponse<Void> adminDelete(@PathVariable long postId) {
        service.deleteAsAdmin(postId);
        return ApiResponse.ok(null);
    }
}

