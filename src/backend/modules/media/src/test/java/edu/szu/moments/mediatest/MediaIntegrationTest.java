package edu.szu.moments.mediatest;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.szu.moments.media.*;
import edu.szu.moments.mediatest.TestMediaCollaboratorsConfig.MutableClock;
import edu.szu.moments.mediatest.TestMediaCollaboratorsConfig.TestCollaborators;
import edu.szu.moments.posts.PostModels.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static edu.szu.moments.mediatest.MediaInputAndRangeTest.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(
    classes = TestMediaApplication.class,
    properties = "spring.main.allow-bean-definition-overriding=true"
)
@AutoConfigureMockMvc
class MediaIntegrationTest {
    private static Path tempStorageDir;

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestCollaborators collaborators;
    @Autowired MutableClock clock;
    @Autowired MediaService mediaService;
    @Autowired MediaStorage mediaStorage;
    @Autowired MediaRepository mediaRepository;
    @Autowired PlatformTransactionManager transactionManager;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) throws IOException {
        tempStorageDir = Files.createTempDirectory("m03-media-store-");
        tempStorageDir.toFile().deleteOnExit();

        String envUrl = System.getenv("M03_TEST_DB_URL");
        String url = (envUrl != null && !envUrl.isBlank())
            ? envUrl
            : "jdbc:h2:mem:m03_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        boolean isH2 = url.startsWith("jdbc:h2:");

        Path migrationDir = Files.createTempDirectory("m03-migrations-");
        copyMigration("/fixtures/users-and-dependencies.sql", migrationDir.resolve("V1__test_dependencies.sql"), isH2);
        copyMigration("/db/migration-candidates/m02/posts.sql", migrationDir.resolve("V2__posts.sql"), isH2);
        copyMigration("/db/migration-candidates/m03/media.sql", migrationDir.resolve("V3__media_under_test.sql"), isH2);

        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> Objects.requireNonNullElse(System.getenv("M03_TEST_DB_USER"), "sa"));
        registry.add("spring.datasource.password", () -> Objects.requireNonNullElse(System.getenv("M03_TEST_DB_PASSWORD"), ""));
        registry.add("spring.flyway.locations", () -> "filesystem:" + migrationDir);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.open-in-view", () -> "false");
        registry.add("spring.jpa.properties.hibernate.jdbc.time_zone", () -> "UTC");
        registry.add("spring.jpa.properties.hibernate.type.preferred_instant_jdbc_type", () -> "TIMESTAMP");
        registry.add("moments.media.storage-dir", () -> tempStorageDir.toString());
        registry.add("moments.media.max-image-bytes", () -> "2048");
        registry.add("moments.media.max-video-bytes", () -> "4096");
        registry.add("moments.media.pending-expire-hours", () -> "24");
        registry.add("spring.main.allow-bean-definition-overriding", () -> "true");
        registry.add("spring.main.banner-mode", () -> "off");
    }

    private static void copyMigration(String resource, Path target, boolean stripMySqlCollationForH2) throws IOException {
        try (InputStream in = MediaIntegrationTest.class.getResourceAsStream(resource)) {
            String sql = new String(Objects.requireNonNull(in, "Missing resource: " + resource).readAllBytes(), StandardCharsets.UTF_8);
            if (stripMySqlCollationForH2) {
                sql = sql.replace("COLLATE=utf8mb4_0900_bin", "")
                         .replace("COLLATE utf8mb4_0900_bin", "");
            }
            Files.writeString(target, sql, StandardCharsets.UTF_8);
        }
        target.toFile().deleteOnExit();
        target.getParent().toFile().deleteOnExit();
    }

    @BeforeEach
    void resetState() throws IOException {
        for (String table : List.of("fixture_audit", "fixture_ratings", "media", "post_tags", "posts", "tags", "users")) {
            jdbc.update("DELETE FROM " + table);
        }
        jdbc.update(
            "INSERT INTO users(id, nickname, role, status) VALUES " +
            "(1, '测试用户甲', 'USER', 'ACTIVE'), " +
            "(2, '测试用户乙', 'USER', 'ACTIVE'), " +
            "(9, '测试管理员', 'ADMIN', 'ACTIVE')"
        );
        if (Files.exists(tempStorageDir)) {
            try (Stream<Path> walk = Files.walk(tempStorageDir)) {
                walk.sorted((a, b) -> b.compareTo(a))
                    .filter(p -> !p.equals(tempStorageDir))
                    .forEach(p -> {
                        try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                    });
            }
        }
        clock.set(Instant.parse("2026-11-18T10:00:00Z"));
        collaborators.as(1, Role.USER);
    }

    @AfterEach
    void tearDown() {
        collaborators.logout();
    }

    private long uploadMedia(MockMultipartFile file) throws Exception {
        String body = mvc.perform(multipart("/api/v1/media").file(file))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("OK"))
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode data = json.readTree(body).get("data");
        return data.get("mediaId").asLong();
    }

    private long createPost(String payloadJson) throws Exception {
        String body = mvc.perform(post("/api/v1/posts").contentType("application/json").content(payloadJson))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("OK"))
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return json.readTree(body).path("data").path("id").asLong();
    }

    private long countFilesOnDisk() throws IOException {
        try (Stream<Path> walk = Files.walk(tempStorageDir)) {
            return walk.filter(Files::isRegularFile).count();
        }
    }

    @Test
    void C01_uploadsValidFormatsAndRejectsOversizedFilesWithoutResidue() throws Exception {
        long jpgId = uploadMedia(new MockMultipartFile("file", "../../unsafe/campus.JPG", "image/jpeg", jpegBytes(512)));
        long pngId = uploadMedia(new MockMultipartFile("file", "icon.png", "image/png", pngBytes(512)));
        long webpId = uploadMedia(new MockMultipartFile("file", "card.webp", "image/webp", webpBytes(512)));
        long mp4Id = uploadMedia(new MockMultipartFile("file", "vlog.mp4", "video/mp4", mp4Bytes(1024)));

        MediaEntity jpg = mediaRepository.findById(jpgId).orElseThrow();
        assertThat(jpg.getStorageKey())
            .matches("^2026/11/18/[0-9a-f-]{36}\\.jpg$")
            .doesNotContain("campus")
            .doesNotContain("..");
        assertThat(countFilesOnDisk()).isEqualTo(4);

        // 超限图片 (2049 > 2048) 与超限视频 (4097 > 4096) 返回 413 且不新增记录或磁盘文件
        mvc.perform(multipart("/api/v1/media").file(new MockMultipartFile("file", "over.jpg", "image/jpeg", jpegBytes(2049))))
            .andExpect(status().isPayloadTooLarge())
            .andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));

        mvc.perform(multipart("/api/v1/media").file(new MockMultipartFile("file", "over.mp4", "video/mp4", mp4Bytes(4097))))
            .andExpect(status().isPayloadTooLarge())
            .andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));

        assertThat(mediaRepository.count()).isEqualTo(4);
        assertThat(countFilesOnDisk()).isEqualTo(4);
        assertThat(List.of(jpgId, pngId, webpId, mp4Id)).hasSize(4);
    }

    @Test
    void C02_rejectsInvalidTypeSpoofedMimeAndUnauthorizedRolesWithoutResidue() throws Exception {
        for (MockMultipartFile bad : List.of(
                new MockMultipartFile("file", "empty.jpg", "image/jpeg", new byte[0]),
                new MockMultipartFile("file", "note.txt", "text/plain", "text".getBytes(StandardCharsets.UTF_8)),
                new MockMultipartFile("file", "fake.jpg", "image/jpeg", "fake-binary".getBytes(StandardCharsets.UTF_8)),
                new MockMultipartFile("file", "bad.gif", "image/jpeg", jpegBytes(128)))) {
            mvc.perform(multipart("/api/v1/media").file(bad))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isIn(400, 415));
        }

        // 未登录 -> 401，管理员上传 -> 403
        collaborators.logout();
        mvc.perform(multipart("/api/v1/media").file(new MockMultipartFile("file", "a.jpg", "image/jpeg", jpegBytes(128))))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        collaborators.as(9, Role.ADMIN);
        mvc.perform(multipart("/api/v1/media").file(new MockMultipartFile("file", "a.jpg", "image/jpeg", jpegBytes(128))))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertThat(mediaRepository.count()).isZero();
        assertThat(countFilesOnDisk()).isZero();
    }

    @Test
    void C03_blocksPendingAndDeletedPostMediaWhileAllowingPublishedMedia() throws Exception {
        byte[] imgBytes = jpegBytes(600);
        long mediaId = uploadMedia(new MockMultipartFile("file", "view.jpg", "image/jpeg", imgBytes));

        // PENDING 状态尚未绑定动态：公开读取拒绝 (404)
        collaborators.logout();
        mvc.perform(get("/api/v1/media/" + mediaId + "/content"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        // 用户甲发布动态绑定该媒体
        collaborators.as(1, Role.USER);
        long postId = createPost("{\"text\":\"图文动态\",\"mediaIds\":[" + mediaId + "],\"tags\":[\"校园\"]}");

        // 公开详情与公开媒体内容均可正常读取
        collaborators.logout();
        mvc.perform(get("/api/v1/posts/" + postId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.media[0].id").value(mediaId))
            .andExpect(jsonPath("$.data.media[0].url").value("/api/v1/media/" + mediaId + "/content"));

        mvc.perform(get("/api/v1/media/" + mediaId + "/content"))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/jpeg"))
            .andExpect(header().string(HttpHeaders.ACCEPT_RANGES, "bytes"))
            .andExpect(content().bytes(imgBytes));

        // 作者删除动态后，媒体通过 PostAccess 校验立即不可公开读取 (404)
        collaborators.as(1, Role.USER);
        mvc.perform(delete("/api/v1/posts/" + postId)).andExpect(status().isOk());

        collaborators.logout();
        mvc.perform(get("/api/v1/media/" + mediaId + "/content"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void C03_hidesMediaWhenAdminDeletesPostOrAuthorIsDisabled() throws Exception {
        long media1 = uploadMedia(new MockMultipartFile("file", "p1.png", "image/png", pngBytes(400)));
        long media2 = uploadMedia(new MockMultipartFile("file", "p2.png", "image/png", pngBytes(400)));
        long post1 = createPost("{\"text\":\"动态1\",\"mediaIds\":[" + media1 + "]}");
        long post2 = createPost("{\"text\":\"动态2\",\"mediaIds\":[" + media2 + "]}");

        // 管理员删除 post1 -> media1 不可读
        collaborators.as(9, Role.ADMIN);
        mvc.perform(delete("/api/v1/admin/posts/" + post1)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/media/" + media1 + "/content")).andExpect(status().isNotFound());

        // 禁用作者用户 1 -> media2 同样被 PostAccess 拦截返回 404
        jdbc.update("UPDATE users SET status = 'DISABLED' WHERE id = 1");
        mvc.perform(get("/api/v1/media/" + media2 + "/content")).andExpect(status().isNotFound());
        assertThat(post2).isPositive();
    }

    @Test
    void C04_supportsFullAndByteRangeStreamingForPublishedMp4And416ForInvalidRange() throws Exception {
        byte[] videoBytes = mp4Bytes(1200);
        long videoId = uploadMedia(new MockMultipartFile("file", "demo.mp4", "video/mp4", videoBytes));
        createPost("{\"text\":\"校园视频\",\"mediaIds\":[" + videoId + "]}");

        collaborators.logout();

        // 1. 普通请求 -> 200 OK
        mvc.perform(get("/api/v1/media/" + videoId + "/content"))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "video/mp4"))
            .andExpect(header().string(HttpHeaders.ACCEPT_RANGES, "bytes"))
            .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, 1200L))
            .andExpect(content().bytes(videoBytes));

        // 2. 合法前段 Range: bytes=0-199 -> 206 Partial Content
        mvc.perform(get("/api/v1/media/" + videoId + "/content")
                .header(HttpHeaders.RANGE, "bytes=0-199"))
            .andExpect(status().isPartialContent())
            .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 0-199/1200"))
            .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, 200L))
            .andExpect(content().bytes(Arrays.copyOfRange(videoBytes, 0, 200)));

        // 3. 拖动进度条中段 Range: bytes=400-899 -> 206 Partial Content
        mvc.perform(get("/api/v1/media/" + videoId + "/content")
                .header(HttpHeaders.RANGE, "bytes=400-899"))
            .andExpect(status().isPartialContent())
            .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 400-899/1200"))
            .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, 500L))
            .andExpect(content().bytes(Arrays.copyOfRange(videoBytes, 400, 900)));

        // 4. 不可满足 Range: bytes=2000-2500 -> 416 Range Not Satisfiable
        mvc.perform(get("/api/v1/media/" + videoId + "/content")
                .header(HttpHeaders.RANGE, "bytes=2000-2500"))
            .andExpect(status().isRequestedRangeNotSatisfiable())
            .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes */1200"))
            .andExpect(jsonPath("$.code").value("RANGE_NOT_SATISFIABLE"));
    }

    @Test
    void C05_enforcesMediaOwnershipPreventsReuseAndSupportsEditKeepAndReplace() throws Exception {
        long img1 = uploadMedia(new MockMultipartFile("file", "1.jpg", "image/jpeg", jpegBytes(300)));
        long img2 = uploadMedia(new MockMultipartFile("file", "2.jpg", "image/jpeg", jpegBytes(300)));
        long img3 = uploadMedia(new MockMultipartFile("file", "3.jpg", "image/jpeg", jpegBytes(300)));

        // 用户乙尝试绑定用户甲上传的 img1 -> 403 FORBIDDEN，且不产生半成品动态
        collaborators.as(2, Role.USER);
        mvc.perform(post("/api/v1/posts").contentType("application/json")
                .content("{\"text\":\"盗用媒体\",\"mediaIds\":[" + img1 + "]}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        // 用户甲正常创建动态绑定 [img1, img2]
        collaborators.as(1, Role.USER);
        long postA = createPost("{\"text\":\"动态A\",\"mediaIds\":[" + img1 + "," + img2 + "]}");

        // 用户甲尝试将已绑定 postA 的 img1 再次绑定到新动态 postB -> 409 CONFLICT
        mvc.perform(post("/api/v1/posts").contentType("application/json")
                .content("{\"text\":\"重复绑定\",\"mediaIds\":[" + img1 + "]}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("CONFLICT"));

        // 用户甲编辑 postA，保留 img2 并替换为 [img2, img3]，移除 img1
        mvc.perform(patch("/api/v1/posts/" + postA).contentType("application/json")
                .content("{\"mediaIds\":[" + img2 + "," + img3 + "]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.media.length()").value(2))
            .andExpect(jsonPath("$.data.media[0].id").value(img2))
            .andExpect(jsonPath("$.data.media[1].id").value(img3));

        // 被编辑移除的 img1 已解绑为 PENDING，不再允许公开读取 (404)；img2 与 img3 正常可读 (200)
        collaborators.logout();
        mvc.perform(get("/api/v1/media/" + img1 + "/content")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/media/" + img2 + "/content")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/media/" + img3 + "/content")).andExpect(status().isOk());
    }

    @Test
    void C06_cleansOnlyExpiredPendingAndEditRemovedMediaWhileKeepingActiveAndFreshMedia() throws Exception {
        // T0 (10:00): 上传 timeoutPending（一直不绑定）、removedByEdit（先绑定后在 10:30 解绑）、activeBound（保持绑定）
        long timeoutPending = uploadMedia(new MockMultipartFile("file", "old.jpg", "image/jpeg", jpegBytes(300)));
        long removedByEdit = uploadMedia(new MockMultipartFile("file", "edit.jpg", "image/jpeg", jpegBytes(300)));
        long activeBound = uploadMedia(new MockMultipartFile("file", "active.jpg", "image/jpeg", jpegBytes(300)));

        long postId = createPost("{\"text\":\"测试清理\",\"mediaIds\":[" + removedByEdit + "," + activeBound + "]}");

        // 推进 30 分钟 (10:30)：编辑动态移除 removedByEdit，保留 activeBound
        clock.advance(Duration.ofMinutes(30));
        mvc.perform(patch("/api/v1/posts/" + postId).contentType("application/json")
                .content("{\"mediaIds\":[" + activeBound + "]}"))
            .andExpect(status().isOk());

        // 推进 23 小时 (次日 09:30)：上传 freshPending
        clock.advance(Duration.ofHours(23));
        long freshPending = uploadMedia(new MockMultipartFile("file", "fresh.jpg", "image/jpeg", jpegBytes(300)));

        // 再推进 2 小时 (次日 11:30)：此时 timeoutPending (25.5h) 与 removedByEdit (解绑后 25h) 均已超过 24h，freshPending 仅 2h
        clock.advance(Duration.ofHours(2));
        var summary = mediaService.cleanupExpiredPending();

        assertThat(summary.cleanedCount()).isEqualTo(2);
        assertThat(summary.mediaIds()).containsExactlyInAnyOrder(timeoutPending, removedByEdit);

        // 验证已超时文件从磁盘移除且状态为 DELETED，活跃文件与未超时文件仍完好
        assertThat(mediaRepository.findById(timeoutPending).orElseThrow().getStatus().name()).isEqualTo("DELETED");
        assertThat(mediaRepository.findById(removedByEdit).orElseThrow().getStatus().name()).isEqualTo("DELETED");
        assertThat(mediaRepository.findById(freshPending).orElseThrow().getStatus().name()).isEqualTo("PENDING");
        assertThat(mediaRepository.findById(activeBound).orElseThrow().getStatus().name()).isEqualTo("ACTIVE");
        assertThat(countFilesOnDisk()).isEqualTo(2);

        collaborators.logout();
        mvc.perform(get("/api/v1/media/" + activeBound + "/content")).andExpect(status().isOk());
    }

    @Test
    void C06_cleansUpFilesOnStreamFailureAndDatabaseTransactionRollback() throws Exception {
        // 1. 文件流读取/写入中途抛 IOException：不产生数据库记录或残留文件
        MockMultipartFile brokenStreamFile = new MockMultipartFile("file", "broken.jpg", "image/jpeg", jpegBytes(256)) {
            private int calls = 0;
            @Override
            public InputStream getInputStream() throws IOException {
                calls++;
                if (calls == 1) {
                    return new ByteArrayInputStream(jpegBytes(256));
                }
                throw new IOException("模拟磁盘写入流中断");
            }
        };
        mvc.perform(multipart("/api/v1/media").file(brokenStreamFile))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
        assertThat(mediaRepository.count()).isZero();
        assertThat(countFilesOnDisk()).isZero();

        // 2. 数据库事务回滚时：通过 TransactionSynchronization 自动删除已落盘物理文件
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        MockMultipartFile validFile = new MockMultipartFile("file", "rollback.jpg", "image/jpeg", jpegBytes(256));
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            mediaService.upload(validFile);
            throw new IllegalStateException("模拟外层事务失败回滚");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(mediaRepository.count()).isZero();
        assertThat(countFilesOnDisk()).isZero();
    }
}
