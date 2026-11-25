package edu.szu.moments.media;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

import edu.szu.moments.media.MediaModels.*;
import edu.szu.moments.posts.*;
import edu.szu.moments.posts.PostModels.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional(readOnly = true)
public class MediaService implements MediaPort {
    private final MediaRepository repository;
    private final MediaInspector inspector;
    private final MediaStorage storage;
    private final MediaProperties properties;
    private final IdentityPort identity;
    private final PostAccess postAccess;
    private final Clock clock;

    public MediaService(MediaRepository repository, MediaInspector inspector, MediaStorage storage,
                        MediaProperties properties, IdentityPort identity, PostAccess postAccess,
                        @Qualifier("mediaClock") Clock clock) {
        this.repository = repository;
        this.inspector = inspector;
        this.storage = storage;
        this.properties = properties;
        this.identity = identity;
        this.postAccess = postAccess;
        this.clock = clock;
    }

    @Transactional
    public UploadedMedia upload(MultipartFile file) {
        Actor actor = requireUser();
        identity.lockActiveActor(actor);
        InspectedFile inspected = inspector.inspect(file);
        Instant now = clock.instant();
        String storageKey = storage.store(file, inspected.extension(), now);
        registerRollbackFileCleanup(storageKey);
        try {
            MediaEntity entity = new MediaEntity(
                actor.id(),
                inspected.type(),
                inspected.mimeType(),
                inspected.originalName(),
                inspected.size(),
                storageKey,
                now
            );
            repository.saveAndFlush(entity);
            String url = properties.contentUrl(entity.getId());
            return new UploadedMedia(
                entity.getId(),
                entity.getId(),
                entity.getType(),
                entity.getMimeType(),
                entity.getFileSize(),
                url,
                entity.getStatus(),
                entity.getCreatedAt()
            );
        } catch (RuntimeException ex) {
            storage.deleteIfExists(storageKey);
            throw ex;
        }
    }

    public MediaStream readPublishedContent(long mediaId, String rangeHeader) {
        MediaEntity entity = repository.findById(mediaId).orElseThrow(MediaFailure::notFound);
        if (entity.getStatus() != MediaStatus.ACTIVE || entity.getPostId() == null) {
            throw MediaFailure.notFound();
        }
        // 通过 M02 的 PostAccess 检查父动态与作者均处于 ACTIVE 状态
        postAccess.requireVisible(entity.getPostId());
        if (!storage.exists(entity.getStorageKey())) {
            throw MediaFailure.notFound();
        }
        long totalSize = entity.getFileSize();
        if (rangeHeader == null || rangeHeader.isBlank()) {
            ByteRange full = new ByteRange(0, totalSize - 1, totalSize);
            byte[] bytes = storage.readRange(entity.getStorageKey(), 0, totalSize);
            return new MediaStream(entity.getMimeType(), totalSize, full, false, bytes);
        }
        ByteRange range = RangeParser.parse(rangeHeader, totalSize);
        byte[] slice = storage.readRange(entity.getStorageKey(), range.start(), range.length());
        return new MediaStream(entity.getMimeType(), totalSize, range, true, slice);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public List<MediaCandidate> lockAndInspect(List<Long> mediaIds) {
        if (mediaIds == null || mediaIds.isEmpty()) {
            return List.of();
        }
        List<Long> sortedIds = mediaIds.stream().distinct().sorted().toList();
        return repository.lockByIds(sortedIds).stream()
            .filter(entity -> storage.exists(entity.getStorageKey()))
            .map(entity -> new MediaCandidate(
                entity.getId(),
                entity.getUploadedBy(),
                entity.getPostId(),
                entity.getType(),
                entity.getStatus()
            ))
            .toList();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void replaceBindings(long actorId, long postId, List<Long> mediaIds, Instant now) {
        List<Long> requested = mediaIds == null ? List.of() : mediaIds;
        if (requested.size() > 9 || new HashSet<>(requested).size() != requested.size()) {
            throw PostFailure.invalid("媒体数量或 ID 无效");
        }

        List<MediaEntity> currentBound = repository.lockActiveByPostId(postId);
        Set<Long> allToLock = new TreeSet<>(requested);
        currentBound.forEach(m -> allToLock.add(m.getId()));

        Map<Long, MediaEntity> lockedById = new HashMap<>();
        if (!allToLock.isEmpty()) {
            repository.lockByIds(allToLock).forEach(m -> lockedById.put(m.getId(), m));
        }

        int images = 0;
        int videos = 0;
        for (long id : requested) {
            MediaEntity item = lockedById.get(id);
            if (item == null || item.getStatus() == MediaStatus.DELETED || !storage.exists(item.getStorageKey())) {
                throw new PostFailure(404, "NOT_FOUND", "媒体不存在");
            }
            if (item.getUploadedBy() != actorId) {
                throw PostFailure.forbidden("不能使用他人上传的媒体");
            }
            if (item.getPostId() != null && !item.getPostId().equals(postId)) {
                throw PostFailure.conflict("媒体已绑定其他动态");
            }
            if (item.getType() == MediaType.IMAGE) {
                images++;
            } else if (item.getType() == MediaType.VIDEO) {
                videos++;
            }
        }
        if ((images > 0 && videos > 0) || videos > 1) {
            throw PostFailure.invalid("只能选择 1–9 张图片或 1 个视频");
        }

        Set<Long> requestedSet = new HashSet<>(requested);
        for (MediaEntity existing : currentBound) {
            if (!requestedSet.contains(existing.getId())) {
                existing.unbindToPending(now);
            }
        }
        for (int i = 0; i < requested.size(); i++) {
            MediaEntity target = lockedById.get(requested.get(i));
            target.bindToPost(postId, i, now);
        }
        repository.flush();
    }

    @Override
    public Map<Long, List<MediaView>> findByPostIds(Set<Long> postIds) {
        Map<Long, List<MediaView>> result = new HashMap<>();
        if (postIds == null || postIds.isEmpty()) {
            return result;
        }
        postIds.forEach(id -> result.put(id, new ArrayList<>()));
        for (MediaEntity entity : repository.findActiveByPostIds(postIds)) {
            result.get(entity.getPostId()).add(new MediaView(
                entity.getId(),
                entity.getType(),
                properties.contentUrl(entity.getId())
            ));
        }
        return result;
    }

    @Transactional
    public CleanupSummary cleanupExpiredPending() {
        Instant now = clock.instant();
        Instant cutoff = now.minus(Duration.ofHours(properties.getPendingExpireHours()));
        List<MediaEntity> expired = repository.lockExpiredPending(cutoff);
        List<Long> cleanedIds = new ArrayList<>();
        for (MediaEntity entity : expired) {
            storage.deleteIfExists(entity.getStorageKey());
            entity.markDeleted(now);
            cleanedIds.add(entity.getId());
        }
        repository.flush();
        return new CleanupSummary(cleanedIds.size(), List.copyOf(cleanedIds));
    }

    private Actor requireUser() {
        Actor actor = identity.requireActor();
        if (actor == null || actor.id() < 1 || actor.role() == null) {
            throw PostFailure.unauthenticated();
        }
        if (actor.role() != Role.USER) {
            throw PostFailure.forbidden("当前角色无权执行此操作");
        }
        return actor;
    }

    private void registerRollbackFileCleanup(String storageKey) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) {
                        storage.deleteIfExists(storageKey);
                    }
                }
            });
        }
    }
}
