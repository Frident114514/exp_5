package edu.szu.moments.media;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MediaCleanupTask {
    private static final Logger LOG = LoggerFactory.getLogger(MediaCleanupTask.class);
    private final MediaService mediaService;

    public MediaCleanupTask(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    @Scheduled(cron = "${moments.media.cleanup-cron:0 */30 * * * *}")
    public void cleanExpired() {
        var summary = mediaService.cleanupExpiredPending();
        if (summary.cleanedCount() > 0) {
            LOG.info("已清理超时未绑定媒体 {} 条: {}", summary.cleanedCount(), summary.mediaIds());
        }
    }
}
