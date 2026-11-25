package edu.szu.moments.media;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "moments.media")
public class MediaProperties {
    /** 本地媒体文件存储目录，可通过环境变量 MEDIA_STORAGE_DIR 或 moments.media.storage-dir 注入 */
    private String storageDir = "./data/media-storage";

    /** 单张图片大小上限：10 MB */
    private long maxImageBytes = 10L * 1024 * 1024;

    /** 单个 MP4 视频大小上限：50 MB（遵循 specs/proposal.md 与 specs/contracts/api.md） */
    private long maxVideoBytes = 50L * 1024 * 1024;

    /** 未绑定或解绑后 PENDING 媒体的保留时长（默认 24 小时） */
    private long pendingExpireHours = 24L;

    /** 媒体内容读取路径前缀 */
    private String contentBasePath = "/api/v1/media";

    public String getStorageDir() { return storageDir; }
    public void setStorageDir(String storageDir) { this.storageDir = storageDir; }

    public long getMaxImageBytes() { return maxImageBytes; }
    public void setMaxImageBytes(long maxImageBytes) { this.maxImageBytes = maxImageBytes; }

    public long getMaxVideoBytes() { return maxVideoBytes; }
    public void setMaxVideoBytes(long maxVideoBytes) { this.maxVideoBytes = maxVideoBytes; }

    public long getPendingExpireHours() { return pendingExpireHours; }
    public void setPendingExpireHours(long pendingExpireHours) { this.pendingExpireHours = pendingExpireHours; }

    public String getContentBasePath() { return contentBasePath; }
    public void setContentBasePath(String contentBasePath) { this.contentBasePath = contentBasePath; }

    public String contentUrl(long mediaId) {
        String prefix = contentBasePath.endsWith("/")
            ? contentBasePath.substring(0, contentBasePath.length() - 1)
            : contentBasePath;
        return prefix + "/" + mediaId + "/content";
    }
}
