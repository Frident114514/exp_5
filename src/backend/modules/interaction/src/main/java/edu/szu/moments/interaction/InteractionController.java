package edu.szu.moments.interaction;

import static edu.szu.moments.interaction.InteractionModels.*;
import static edu.szu.moments.posts.PostModels.PageView;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.*;

import edu.szu.moments.posts.ApiResponse;

@RestController
@RequestMapping("/api/v1")
public class InteractionController {
    private final InteractionService service;
    private final InteractionPayloadParser parser;

    public InteractionController(InteractionService service, InteractionPayloadParser parser) {
        this.service = service;
        this.parser = parser;
    }

    @PutMapping("/posts/{postId}/rating")
    public ApiResponse<RatingResult> rate(@PathVariable long postId, @RequestBody JsonNode body) {
        return ApiResponse.ok(service.rate(postId, parser.parseScore(body)));
    }

    @GetMapping("/posts/{postId}/comments")
    public ApiResponse<PageView<CommentView>> comments(
            @PathVariable long postId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResponse.ok(service.listComments(postId, page, pageSize));
    }

    @PostMapping("/posts/{postId}/comments")
    public ApiResponse<CommentView> addComment(
            @PathVariable long postId, @RequestBody JsonNode body) {
        return ApiResponse.ok(service.addComment(postId, parser.parseComment(body)));
    }
}
