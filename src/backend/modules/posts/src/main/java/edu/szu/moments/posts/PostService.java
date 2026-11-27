package edu.szu.moments.posts;

import java.time.Clock;
import java.util.*;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static edu.szu.moments.posts.PostModels.*;

@Service
@Transactional(readOnly = true)
public class PostService {
    private final PostRepository posts;
    private final TagRepository tags;
    private final AuthorRepository authors;
    private final PostAccess access;
    private final IdentityPort identity;
    private final MediaPort media;
    private final RatingPort ratings;
    private final AdminAuditPort audit;
    private final Clock clock;
    private final org.springframework.context.ApplicationEventPublisher events;

    public PostService(PostRepository posts, TagRepository tags, AuthorRepository authors,
                       PostAccess access, IdentityPort identity, MediaPort media,
                       RatingPort ratings, AdminAuditPort audit,
                       @Qualifier("postsClock") Clock clock,
                       org.springframework.context.ApplicationEventPublisher events) {
        this.events = events;
        this.posts = posts;
        this.tags = tags;
        this.authors = authors;
        this.access = access;
        this.identity = identity;
        this.media = media;
        this.ratings = ratings;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public CreatedPost create(PostChange change) {
        Actor actor = requireRole(Role.USER);
        identity.lockActiveActor(actor);
        verifyMedia(actor.id(), null, change.text(), change.mediaIds());
        var now = clock.instant();
        PostEntity post = new PostEntity(actor.id(), change.text(), now);
        post.edit(change.text(), resolveTags(change.tags()), now);
        posts.saveAndFlush(post);
        media.replaceBindings(actor.id(), post.getId(), change.mediaIds(), now);
        events.publishEvent(new UserActivity(actor.id()));
        return new CreatedPost(post.getId());
    }

    @Transactional
    public PostView edit(long id, PostChange change) {
        Actor actor = requireRole(Role.USER);
        identity.lockActiveActor(actor);
        PostEntity post = access.lockVisibleEntity(id);
        requireOwner(actor, post);
        String text = change.text() == null ? post.getText() : change.text();
        List<Long> mediaIds = change.mediaIds() == null
            ? mediaFor(Set.of(id)).get(id).stream().map(MediaView::id).toList()
            : change.mediaIds();
        verifyMedia(actor.id(), id, text, mediaIds);
        var now = clock.instant();
        post.edit(text, change.tags() == null ? null : resolveTags(change.tags()), now);
        if (change.mediaIds() != null) {
            media.replaceBindings(actor.id(), id, mediaIds, now);
        }
        posts.flush();
        events.publishEvent(new UserActivity(actor.id()));
        return views(List.of(post)).getFirst();
    }

    @Transactional
    public void deleteOwn(long id) {
        Actor actor = requireRole(Role.USER);
        identity.lockActiveActor(actor);
        PostEntity post = access.lockVisibleEntity(id);
        requireOwner(actor, post);
        post.delete(clock.instant());
    }

    @Transactional
    public void deleteAsAdmin(long id) {
        Actor actor = requireRole(Role.ADMIN);
        identity.lockActiveActor(actor);
        PostEntity post = access.lockVisibleEntity(id);
        var now = clock.instant();
        post.delete(now);
        audit.recordDeletePost(actor.id(), id, now);
    }

    public PostView detail(long id) {
        return views(List.of(access.visibleEntity(id))).getFirst();
    }

    public PageView<PostView> publicList(PostFilter filter) {
        return list(filter);
    }

    public PageView<PostView> mine(int page, int pageSize) {
        Actor actor = requireRole(Role.USER);
        return list(PostFilter.parse(null, null, null, null, actor.id(), page, pageSize));
    }

    public PageView<PostView> adminList(PostFilter filter) {
        requireRole(Role.ADMIN);
        return list(filter);
    }

    private PageView<PostView> list(PostFilter filter) {
        var pageable = PageRequest.of(filter.page() - 1, filter.pageSize(),
            Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        var page = posts.findAll(PostSpecifications.visible(filter), pageable);
        return new PageView<>(views(page.getContent()), filter.page(), filter.pageSize(), page.getTotalElements());
    }

    private Actor requireRole(Role role) {
        Actor actor = identity.requireActor();
        if (actor == null || actor.id() < 1 || actor.role() == null) {
            throw PostFailure.unauthenticated();
        }
        if (actor.role() != role) {
            throw PostFailure.forbidden("当前角色无权执行此操作");
        }
        return actor;
    }

    private void requireOwner(Actor actor, PostEntity post) {
        if (post.getAuthorId() != actor.id()) {
            throw PostFailure.forbidden("只能管理本人动态");
        }
    }

    private void verifyMedia(long actorId, Long postId, String text, List<Long> ids) {
        if ((text == null || text.isBlank()) && ids.isEmpty()) {
            throw PostFailure.invalid("文字和媒体至少填写一项");
        }
        if (ids.size() > 9 || new HashSet<>(ids).size() != ids.size()) {
            throw PostFailure.invalid("媒体数量或 ID 无效");
        }
        if (ids.isEmpty()) {
            return;
        }
        var candidates = media.lockAndInspect(ids.stream().sorted().toList());
        if (candidates == null) {
            throw PostFailure.dependency();
        }
        Map<Long, MediaCandidate> byId = new HashMap<>();
        candidates.forEach(item -> byId.put(item.id(), item));
        int images = 0;
        int videos = 0;
        for (long id : ids) {
            MediaCandidate item = byId.get(id);
            if (item == null || item.status() == MediaStatus.DELETED) {
                throw new PostFailure(404, "NOT_FOUND", "媒体不存在");
            }
            if (item.uploadedBy() != actorId) {
                throw PostFailure.forbidden("不能使用他人上传的媒体");
            }
            if (item.postId() != null && !item.postId().equals(postId)) {
                throw PostFailure.conflict("媒体已绑定其他动态");
            }
            if (item.type() == MediaType.IMAGE) {
                images++;
            } else if (item.type() == MediaType.VIDEO) {
                videos++;
            } else {
                throw PostFailure.invalid("不支持的媒体类型");
            }
        }
        if ((images > 0 && videos > 0) || videos > 1) {
            throw PostFailure.invalid("只能选择 1–9 张图片或 1 个视频");
        }
    }

    private Set<TagEntity> resolveTags(List<String> names) {
        if (names.isEmpty()) {
            return new LinkedHashSet<>();
        }
        // 固定加锁顺序，并用数据库唯一键处理不同用户同时创建相同标签。
        names.stream().distinct().sorted().forEach(tags::insertIfAbsent);
        return new LinkedHashSet<>(tags.findByNameIn(names));
    }

    private Map<Long, List<MediaView>> mediaFor(Set<Long> ids) {
        var result = media.findByPostIds(ids);
        if (result == null || ids.stream().anyMatch(id -> result.get(id) == null)) {
            throw PostFailure.dependency();
        }
        return result;
    }

    private List<PostView> views(List<PostEntity> records) {
        if (records.isEmpty()) {
            return List.of();
        }
        Set<Long> ids = records.stream().map(PostEntity::getId).collect(Collectors.toSet());
        Set<Long> authorIds = records.stream().map(PostEntity::getAuthorId).collect(Collectors.toSet());
        var authorMap = authors.findByIdInAndStatus(authorIds, "ACTIVE").stream()
            .collect(Collectors.toMap(AuthorRecord::getId, a -> new Author(a.getId(), a.getNickname())));
        var mediaMap = mediaFor(ids);
        var ratingMap = ratings.summarize(ids);
        if (ratingMap == null || ids.stream().anyMatch(id -> ratingMap.get(id) == null)) {
            throw PostFailure.dependency();
        }
        Map<Long, List<String>> tagMap = new HashMap<>();
        posts.findTagAssignments(ids).forEach(t ->
            tagMap.computeIfAbsent(t.postId(), ignored -> new ArrayList<>()).add(t.name()));
        return records.stream().map(post -> {
            Author author = authorMap.get(post.getAuthorId());
            if (author == null) {
                throw PostFailure.missing();
            }
            return new PostView(post.getId(), author, post.getText(),
                List.copyOf(mediaMap.get(post.getId())),
                List.copyOf(tagMap.getOrDefault(post.getId(), List.of())),
                post.getCreatedAt(), post.getUpdatedAt(), ratingMap.get(post.getId()));
        }).toList();
    }
}

