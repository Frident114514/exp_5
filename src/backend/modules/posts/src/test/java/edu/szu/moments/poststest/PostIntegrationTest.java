package edu.szu.moments.poststest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.szu.moments.posts.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static edu.szu.moments.posts.PostModels.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = TestApplication.class)
@AutoConfigureMockMvc
class PostIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestPorts ports;
    @Autowired PostService service;
    @Autowired PostAccess access;
    @Autowired AuthorPosts authorPosts;
    @Autowired PlatformTransactionManager transactionManager;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) throws IOException {
        String url = Objects.requireNonNull(System.getenv("M02_TEST_DB_URL"),
            "请使用 python3 scripts/run_mysql_tests.py 启动独立临时 MySQL 8.4 测试环境");
        Path dir = Files.createTempDirectory("m02-migrations-");
        copy("/fixtures/users-and-dependencies.sql", dir.resolve("V1__test_dependencies.sql"));
        copy("/db/migration-candidates/m02/posts.sql", dir.resolve("V2__posts_under_test.sql"));
        properties.add("spring.datasource.url", () -> url);
        properties.add("spring.datasource.username", () -> System.getenv("M02_TEST_DB_USER"));
        properties.add("spring.datasource.password", () -> System.getenv("M02_TEST_DB_PASSWORD"));
        properties.add("spring.flyway.locations", () -> "filesystem:" + dir);
        properties.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        properties.add("spring.jpa.open-in-view", () -> "false");
        properties.add("spring.jpa.properties.hibernate.jdbc.time_zone", () -> "UTC");
        properties.add("spring.jpa.properties.hibernate.type.preferred_instant_jdbc_type", () -> "TIMESTAMP");
        properties.add("logging.level.org.hibernate.orm.deprecation", () -> "ERROR");
        properties.add("spring.main.banner-mode", () -> "off");
    }

    private static void copy(String resource, Path target) throws IOException {
        try (var input = PostIntegrationTest.class.getResourceAsStream(resource)) {
            Files.copy(Objects.requireNonNull(input), target);
        }
        target.toFile().deleteOnExit();
        target.getParent().toFile().deleteOnExit();
    }

    @BeforeEach
    void reset() {
        for (String table : List.of("fixture_audit", "fixture_ratings", "fixture_media",
                "post_tags", "posts", "tags", "users")) {
            jdbc.update("DELETE FROM " + table);
        }
        jdbc.update("INSERT INTO users(id,nickname,role,status) VALUES (1,'测试用户甲','USER','ACTIVE'),(2,'测试用户乙','USER','ACTIVE'),(9,'测试管理员','ADMIN','ACTIVE')");
        ports.reset();
        ports.as(1, Role.USER);
    }

    @AfterEach
    void logout() { ports.logout(); }

    @Test
    void createsTextWithNormalizedTagsAndPublicDetail() throws Exception {
        long id = create("{\"text\":\" 校园日记 \",\"tags\":[\"校园\",\" 校园 \",\"School\",\"school\"]}");
        ports.logout();
        mvc.perform(get("/api/v1/posts/" + id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.author.id").value(1))
            .andExpect(jsonPath("$.data.text").value("校园日记"))
            .andExpect(jsonPath("$.data.tags.length()").value(3))
            .andExpect(jsonPath("$.data.rating.count").value(0))
            .andExpect(jsonPath("$.data.rating.average").isEmpty());
        assertThat(count("tags")).isEqualTo(3);
        assertThat(count("post_tags")).isEqualTo(3);
    }

    @Test
    void rejectsEmptyContentTooManyImagesAndTagsWithoutPartialPosts() throws Exception {
        for (String body : List.of("{}", "{\"text\":\"  \"}",
                "{\"mediaIds\":[1,2,3,4,5,6,7,8,9,10]}",
                "{\"text\":\"x\",\"tags\":[\"1\",\"2\",\"3\",\"4\",\"5\",\"6\"]}")) {
            mvc.perform(post("/api/v1/posts").contentType("application/json").content(body))
                .andExpect(status().isBadRequest());
        }
        assertThat(count("posts")).isZero();
    }

    @Test
    void createsImageAndVideoPostsAndPreservesMediaOrder() throws Exception {
        media(11, 1, "IMAGE");
        media(12, 1, "IMAGE");
        media(13, 1, "VIDEO");
        long image = create("{\"mediaIds\":[12,11]}");
        long video = create("{\"text\":\"视频\",\"mediaIds\":[13]}");
        mvc.perform(get("/api/v1/posts/" + image))
            .andExpect(jsonPath("$.data.media[0].id").value(12))
            .andExpect(jsonPath("$.data.media[1].id").value(11));
        mvc.perform(get("/api/v1/posts/" + video))
            .andExpect(jsonPath("$.data.media[0].type").value("VIDEO"));
    }

    @Test
    void rejectsMixedOrMultipleVideos() throws Exception {
        media(11, 1, "IMAGE");
        media(12, 1, "VIDEO");
        media(13, 1, "VIDEO");
        for (String body : List.of("{\"mediaIds\":[11,12]}", "{\"mediaIds\":[12,13]}")) {
            mvc.perform(post("/api/v1/posts").contentType("application/json").content(body))
                .andExpect(status().isBadRequest());
        }
        assertThat(count("posts")).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM fixture_media WHERE post_id IS NOT NULL", Long.class)).isZero();
    }

    @Test
    void rejectsOtherAuthorsAndWrongRoles() throws Exception {
        long id = create("{\"text\":\"原内容\"}");
        ports.as(2, Role.USER);
        mvc.perform(patch("/api/v1/posts/" + id).contentType("application/json").content("{\"text\":\"恶意修改\"}"))
            .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/posts/" + id)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/admin/posts/" + id)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/posts")).andExpect(status().isForbidden());
        ports.as(9, Role.ADMIN);
        mvc.perform(post("/api/v1/posts").contentType("application/json").content("{\"text\":\"x\"}"))
            .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT text FROM posts WHERE id=?", String.class, id)).isEqualTo("原内容");
    }

    @Test
    void anonymousMayReadButCannotWriteOrViewMine() throws Exception {
        long id = create("{\"text\":\"公开内容\"}");
        ports.logout();
        mvc.perform(get("/api/v1/posts")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/posts/" + id)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/posts").contentType("application/json").content("{\"text\":\"x\"}"))
            .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/users/me/posts")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/posts")).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsForeignBoundAndMissingMedia() throws Exception {
        media(11, 2, "IMAGE");
        media(12, 1, "IMAGE");
        mvc.perform(post("/api/v1/posts").contentType("application/json").content("{\"mediaIds\":[11]}"))
            .andExpect(status().isForbidden());
        long id = create("{\"mediaIds\":[12]}");
        mvc.perform(post("/api/v1/posts").contentType("application/json").content("{\"mediaIds\":[12]}"))
            .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/posts").contentType("application/json").content("{\"mediaIds\":[9999]}"))
            .andExpect(status().isNotFound());
        assertThat(count("posts")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT post_id FROM fixture_media WHERE id=12", Long.class)).isEqualTo(id);
    }

    @Test
    void patchPreservesOmittedFieldsAndCreationTimeAndCanClearLists() throws Exception {
        media(11, 1, "IMAGE");
        long id = create("{\"text\":\"原文\",\"mediaIds\":[11],\"tags\":[\"校园\"]}");
        String created = response(mvc.perform(get("/api/v1/posts/" + id)).andReturn()).at("/data/createdAt").asText();
        mvc.perform(patch("/api/v1/posts/" + id).contentType("application/json").content("{\"text\":\"新文\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.createdAt").value(created))
            .andExpect(jsonPath("$.data.media[0].id").value(11))
            .andExpect(jsonPath("$.data.tags[0]").value("校园"));
        mvc.perform(patch("/api/v1/posts/" + id).contentType("application/json")
                .content("{\"text\":\"\",\"mediaIds\":[],\"tags\":[]}"))
            .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/v1/posts/" + id).contentType("application/json")
                .content("{\"mediaIds\":[],\"tags\":[]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.text").value("新文"))
            .andExpect(jsonPath("$.data.media").isEmpty())
            .andExpect(jsonPath("$.data.tags").isEmpty());
        assertThat(jdbc.queryForObject("SELECT status FROM fixture_media WHERE id=11", String.class)).isEqualTo("PENDING");
    }

    @Test
    void creationBindingFailureRollsBackPostsTagsAndMedia() throws Exception {
        media(11, 1, "IMAGE");
        ports.failBinding = true;
        mvc.perform(post("/api/v1/posts").contentType("application/json")
                .content("{\"text\":\"回滚\",\"mediaIds\":[11],\"tags\":[\"新标签\"]}"))
            .andExpect(status().isConflict());
        assertThat(count("posts")).isZero();
        assertThat(count("tags")).isZero();
        assertThat(count("post_tags")).isZero();
        assertThat(jdbc.queryForObject("SELECT post_id FROM fixture_media WHERE id=11", Long.class)).isNull();
    }

    @Test
    void editBindingFailureRollsBackTextTagsAndBindings() throws Exception {
        media(11, 1, "IMAGE");
        media(12, 1, "IMAGE");
        long id = create("{\"text\":\"原文\",\"mediaIds\":[11],\"tags\":[\"旧标签\"]}");
        ports.failBinding = true;
        mvc.perform(patch("/api/v1/posts/" + id).contentType("application/json")
                .content("{\"text\":\"新文\",\"mediaIds\":[12],\"tags\":[\"新标签\"]}"))
            .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT text FROM posts WHERE id=?", String.class, id)).isEqualTo("原文");
        assertThat(jdbc.queryForObject("SELECT post_id FROM fixture_media WHERE id=11", Long.class)).isEqualTo(id);
        assertThat(jdbc.queryForObject("SELECT post_id FROM fixture_media WHERE id=12", Long.class)).isNull();
        assertThat(jdbc.queryForList("SELECT name FROM tags", String.class)).containsExactly("旧标签");
    }

    @Test
    void filtersBeijingInclusiveDaysAndTagIntersection() throws Exception {
        long before = dated("2026-11-17T15:59:59.999999Z", "校园");
        long first = dated("2026-11-17T16:00:00Z", "校园");
        long last = dated("2026-11-18T15:59:59.999999Z", "校园");
        long after = dated("2026-11-18T16:00:00Z", "校园");
        dated("2026-11-18T05:00:00Z", "运动");
        var result = response(mvc.perform(get("/api/v1/posts").param("from", "2026-11-18")
            .param("to", "2026-11-18").param("tag", "校园")).andExpect(status().isOk()).andReturn());
        assertThat(ids(result)).containsExactly(last, first);
        assertThat(result.at("/data/total").asInt()).isEqualTo(2);
        var upper = response(mvc.perform(get("/api/v1/posts").param("to", "2026-11-18").param("tag", "校园")).andReturn());
        assertThat(ids(upper)).containsExactly(last, first, before);
        var lower = response(mvc.perform(get("/api/v1/posts").param("from", "2026-11-18").param("tag", "校园")).andReturn());
        assertThat(ids(lower)).containsExactly(after, last, first);
        mvc.perform(get("/api/v1/posts").param("tag", "不存在")).andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void stabilizesPagingForIdenticalCreationTimes() throws Exception {
        List<Long> expected = new ArrayList<>();
        for (int i = 0; i < 5; i++) { expected.add(dated("2026-11-18T02:00:00Z", "同日")); }
        expected.sort(Comparator.reverseOrder());
        var first = response(mvc.perform(get("/api/v1/posts").param("pageSize", "3")).andReturn());
        var second = response(mvc.perform(get("/api/v1/posts").param("pageSize", "3").param("page", "2")).andReturn());
        List<Long> actual = new ArrayList<>(ids(first));
        actual.addAll(ids(second));
        assertThat(actual).containsExactlyElementsOf(expected).doesNotHaveDuplicates();
        assertThat(first.at("/data/total").asInt()).isEqualTo(5);
        mvc.perform(get("/api/v1/posts").param("page", "99")).andExpect(jsonPath("$.data.items").isEmpty());
    }

    @Test
    void searchesAdminLiteralKeywordAuthorDateAndTag() throws Exception {
        long own = create("{\"text\":\"100%_校园\",\"tags\":[\"校园\"]}");
        create("{\"text\":\"100abc校园\",\"tags\":[\"校园\"]}");
        ports.as(2, Role.USER);
        long other = create("{\"text\":\"100%_校园\",\"tags\":[\"校园\"]}");
        ports.as(9, Role.ADMIN);
        var result = response(mvc.perform(get("/api/v1/admin/posts").param("keyword", "%_")
            .param("authorId", "1").param("tag", "校园").param("from", "2026-01-01"))
            .andExpect(status().isOk()).andReturn());
        assertThat(ids(result)).containsExactly(own).doesNotContain(other);
    }

    @Test
    void ownListDoesNotLeakOtherUsersPosts() throws Exception {
        long own = create("{\"text\":\"甲\"}");
        ports.as(2, Role.USER);
        create("{\"text\":\"乙\"}");
        ports.as(1, Role.USER);
        var result = response(mvc.perform(get("/api/v1/users/me/posts")).andExpect(status().isOk()).andReturn());
        assertThat(ids(result)).containsExactly(own);
    }

    @Test
    void authorDeleteHidesEveryPostReadAndSharedAccessEntry() throws Exception {
        long id = create("{\"text\":\"将删除\"}");
        mvc.perform(delete("/api/v1/posts/" + id)).andExpect(status().isOk()).andExpect(jsonPath("$.code").value("OK"));
        mvc.perform(get("/api/v1/posts/" + id)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/posts")).andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(get("/api/v1/users/me/posts")).andExpect(jsonPath("$.data.total").value(0));
        assertThatThrownBy(() -> access.requireVisible(id)).isInstanceOf(PostFailure.class);
        var tx = new TransactionTemplate(transactionManager);
        assertThatThrownBy(() -> tx.execute(status -> access.lockVisibleForInteraction(id))).isInstanceOf(PostFailure.class);
        ports.as(9, Role.ADMIN);
        mvc.perform(get("/api/v1/admin/posts")).andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(delete("/api/v1/admin/posts/" + id)).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT status FROM posts WHERE id=?", String.class, id)).isEqualTo("DELETED");
    }

    @Test
    void adminDeleteAndAuditAreAtomic() throws Exception {
        long id = create("{\"text\":\"管理删除\"}");
        ports.as(9, Role.ADMIN);
        ports.failAudit = true;
        mvc.perform(delete("/api/v1/admin/posts/" + id)).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT status FROM posts WHERE id=?", String.class, id)).isEqualTo("ACTIVE");
        assertThat(count("fixture_audit")).isZero();
        ports.failAudit = false;
        mvc.perform(delete("/api/v1/admin/posts/" + id)).andExpect(status().isOk());
        assertThat(count("fixture_audit")).isEqualTo(1);
        mvc.perform(get("/api/v1/posts/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void authorDisableParticipatesInCallerTransactionAndCanRollback() throws Exception {
        long id = create("{\"text\":\"禁用关联\"}");
        var tx = new TransactionTemplate(transactionManager);
        assertThatThrownBy(() -> tx.execute(status -> {
            jdbc.queryForObject("SELECT id FROM users WHERE id=1 FOR UPDATE", Long.class);
            jdbc.update("UPDATE users SET status='DISABLED' WHERE id=1");
            authorPosts.hideByAuthor(1);
            throw new IllegalStateException("模拟调用方失败");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT status FROM users WHERE id=1", String.class)).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT status FROM posts WHERE id=?", String.class, id)).isEqualTo("ACTIVE");
        tx.execute(status -> {
            jdbc.queryForObject("SELECT id FROM users WHERE id=1 FOR UPDATE", Long.class);
            jdbc.update("UPDATE users SET status='DISABLED' WHERE id=1");
            return authorPosts.hideByAuthor(1);
        });
        mvc.perform(get("/api/v1/posts/" + id)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/users/me/posts")).andExpect(status().isUnauthorized());
        assertThatThrownBy(() -> authorPosts.hideByAuthor(1))
            .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
    }

    @Test
    void filtersDisabledAuthorsEvenBeforePostsAreHidden() throws Exception {
        long id = create("{\"text\":\"禁用用户内容\"}");
        jdbc.update("UPDATE users SET status='DISABLED' WHERE id=1");
        ports.logout();
        mvc.perform(get("/api/v1/posts")).andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(get("/api/v1/posts/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void batchesMediaAndRatingSummariesAndUsesRealAggregateFixture() throws Exception {
        long id = create("{\"text\":\"评分动态\"}");
        create("{\"text\":\"无评分动态\"}");
        jdbc.update("INSERT INTO fixture_ratings(post_id,user_id,score) VALUES (?,2,3),(?,3,4)", id, id);
        ports.mediaReads.set(0);
        ports.ratingReads.set(0);
        var result = response(mvc.perform(get("/api/v1/posts")).andExpect(status().isOk()).andReturn());
        assertThat(ports.mediaReads.get()).isEqualTo(1);
        assertThat(ports.ratingReads.get()).isEqualTo(1);
        assertThat(result.at("/data/items/1/rating/average").decimalValue()).isEqualByComparingTo("3.5");
        assertThat(result.at("/data/items/1/rating/count").asInt()).isEqualTo(2);
    }

    @Test
    void missingDependencyResultsFailAndRollbackEdits() throws Exception {
        long id = create("{\"text\":\"原文\"}");
        ports.missingRatings = true;
        mvc.perform(get("/api/v1/posts/" + id)).andExpect(status().isServiceUnavailable());
        mvc.perform(patch("/api/v1/posts/" + id).contentType("application/json").content("{\"text\":\"不应保存\"}"))
            .andExpect(status().isServiceUnavailable());
        assertThat(jdbc.queryForObject("SELECT text FROM posts WHERE id=?", String.class, id)).isEqualTo("原文");
    }

    @Test
    void enforcesDatabaseForeignKeysAndUniqueness() throws Exception {
        long id = create("{\"text\":\"约束\",\"tags\":[\"校园\"]}");
        long tag = jdbc.queryForObject("SELECT id FROM tags WHERE name='校园'", Long.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO tags(name) VALUES ('校园')")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO post_tags(post_id,tag_id) VALUES (?,?)", id, tag))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO post_tags(post_id,tag_id) VALUES (999999,?)", tag))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE posts SET author_id=999999 WHERE id=?", id))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void supportsConcurrentTagCreationAcrossUsers() throws Exception {
        ports.actorReadBarrier = new CyclicBarrier(2);
        var pool = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            List<Future<Long>> futures = new ArrayList<>();
            for (long user : List.of(1L, 2L)) {
                futures.add(pool.submit(() -> {
                    ports.as(user, Role.USER);
                    try {
                        start.await();
                        return service.create(new PostChange("并发标签", List.of(), List.of("共同标签"))).id();
                    } finally {
                        ports.logout();
                    }
                }));
            }
            start.countDown();
            for (var future : futures) { assertThat(future.get(15, TimeUnit.SECONDS)).isPositive(); }
            assertThat(count("posts")).isEqualTo(2);
            assertThat(count("tags")).isEqualTo(1);
            assertThat(count("post_tags")).isEqualTo(2);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void tagSearchIsCaseSensitiveAndSupportsEmoji() throws Exception {
        long upper = create("{\"text\":\"大写\",\"tags\":[\"School\",\"🌸\"]}");
        create("{\"text\":\"小写\",\"tags\":[\"school\"]}");
        assertThat(ids(response(mvc.perform(get("/api/v1/posts").param("tag", "School")).andReturn()))).containsExactly(upper);
        assertThat(ids(response(mvc.perform(get("/api/v1/posts").param("tag", "🌸")).andReturn()))).containsExactly(upper);
    }

    @Test
    void returnsContractErrorsForBadParametersAndUnknownObjects() throws Exception {
        mvc.perform(get("/api/v1/posts/not-a-number")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(get("/api/v1/posts/999999")).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mvc.perform(get("/api/v1/posts").param("from", "2026-02-30")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/posts").param("page", "0")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/posts").param("pageSize", "51")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/posts").contentType("application/json").content("{bad"))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/posts").contentType("application/json").content("{\"text\":\"x\",\"authorId\":2}"))
            .andExpect(status().isBadRequest());
    }

    private long create(String body) throws Exception {
        var result = mvc.perform(post("/api/v1/posts").contentType("application/json").content(body))
            .andExpect(status().isOk()).andReturn();
        return response(result).at("/data/id").asLong();
    }

    private void media(long id, long owner, String type) {
        jdbc.update("INSERT INTO fixture_media(id,uploaded_by,type,status) VALUES (?,?,?,'PENDING')", id, owner, type);
    }

    private long dated(String time, String tag) throws Exception {
        long id = create("{\"text\":\"日期样例\",\"tags\":[\"" + tag + "\"]}");
        jdbc.update("UPDATE posts SET created_at=?,updated_at=? WHERE id=?",
            Timestamp.from(Instant.parse(time)), Timestamp.from(Instant.parse(time)), id);
        return id;
    }

    private long count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
    }

    private JsonNode response(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsByteArray());
    }

    private List<Long> ids(JsonNode response) {
        List<Long> ids = new ArrayList<>();
        response.at("/data/items").forEach(item -> ids.add(item.get("id").asLong()));
        return ids;
    }
}
