package edu.szu.moments.interaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Transactional;

import edu.szu.moments.interaction.InteractionTestConfiguration.InteractionTestPorts;
import edu.szu.moments.posts.PostAccess;
import edu.szu.moments.posts.PostService;
import edu.szu.moments.posts.PostModels.Role;

@SpringBootTest(classes = InteractionHttpIntegrationTest.TestApplication.class, properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.jpa.properties.hibernate.jdbc.time_zone=UTC"
})
@AutoConfigureMockMvc(addFilters = false)
class InteractionHttpIntegrationTest {
    private static final String NOW = "2026-11-26 12:00:00.123456";

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({edu.szu.moments.posts.PostsModuleConfiguration.class,
        InteractionModuleConfiguration.class, InteractionTestConfiguration.class})
    static class TestApplication {}

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Autowired InteractionService service;
    @Autowired InteractionTestPorts ports;
    @Autowired PostAccess posts;
    @Autowired PostService postService;
    @Autowired PlatformTransactionManager transactionManager;

    @DynamicPropertySource
    static void prepareSchema(DynamicPropertyRegistry properties) throws IOException {
        String url = requiredEnv("M04_TEST_DB_URL");
        String user = requiredEnv("M04_TEST_DB_USER");
        String password = requiredEnv("M04_TEST_DB_PASSWORD");
        Path versions = Files.createTempDirectory("m04-http-migrations-");
        Path v1 = versions.resolve("V1__test_users.sql");
        Path v2 = versions.resolve("V2__m02_posts.sql");
        Path v3 = versions.resolve("V3__m04_interaction.sql");
        try (var users = InteractionHttpIntegrationTest.class.getResourceAsStream("/fixtures/users.sql")) {
            Files.copy(users, v1);
            Files.copy(Path.of("../posts/src/main/resources/db/migration-candidates/m02/posts.sql"), v2);
            Files.copy(Path.of("src/main/resources/db/migration-candidates/m04/interaction.sql"), v3);
            Flyway.configure().dataSource(url, user, password).locations("filesystem:" + versions).load().migrate();
        } finally {
            Files.deleteIfExists(v3);
            Files.deleteIfExists(v2);
            Files.deleteIfExists(v1);
            Files.delete(versions);
        }
        properties.add("spring.datasource.url", () -> url);
        properties.add("spring.datasource.username", () -> user);
        properties.add("spring.datasource.password", () -> password);
    }

    @BeforeEach
    void seed() {
        ports.logout();
        db.update("DELETE FROM comments");
        db.update("DELETE FROM ratings");
        db.update("DELETE FROM post_tags");
        db.update("DELETE FROM posts");
        db.update("DELETE FROM users");
        db.update("INSERT INTO users(id,nickname,role,status) VALUES "
            + "(11,'作者','USER','ACTIVE'),(22,'甲','USER','ACTIVE'),"
            + "(33,'乙','USER','ACTIVE'),(44,'管理员','ADMIN','ACTIVE')");
        db.update("INSERT INTO posts(id,author_id,text,created_at,updated_at) VALUES "
            + "(101,11,'动态一',?,?),(102,33,'动态二',?,?)", NOW, NOW, NOW, NOW);
    }

    @AfterEach
    void clearActor() {
        ports.logout();
    }

    @Test
    void differentUsersCanInteractWithSamePostWhileSharedVisibilityLockIsHeld() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        var lockAcquired = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try {
            var holder = executor.submit(() -> {
                new TransactionTemplate(transactionManager).execute(status -> {
                    posts.lockVisibleForInteraction(101);
                    lockAcquired.countDown();
                    try {
                        assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(interrupted);
                    }
                    return null;
                });
                return null;
            });
            assertThat(lockAcquired.await(5, TimeUnit.SECONDS)).isTrue();

            var interaction = executor.submit(() -> {
                ports.as(22, Role.USER);
                try {
                    service.rate(101, 4);
                } finally {
                    ports.logout();
                }
                return null;
            });

            // A PESSIMISTIC_WRITE interaction lock would block this future until
            // release is counted down. PESSIMISTIC_READ must allow it to finish.
            interaction.get(2, TimeUnit.SECONDS);
            release.countDown();
            holder.get(5, TimeUnit.SECONDS);
            assertThat(db.queryForObject(
                "SELECT COUNT(*) FROM ratings WHERE post_id=101 AND user_id=22", Integer.class))
                .isEqualTo(1);
        } finally {
            release.countDown();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void postDeletionWaitsForInteractionTransactionToFinish() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        var lockAcquired = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try {
            var holder = executor.submit(() -> {
                new TransactionTemplate(transactionManager).execute(status -> {
                    posts.lockVisibleForInteraction(101);
                    lockAcquired.countDown();
                    try {
                        assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(interrupted);
                    }
                    return null;
                });
                return null;
            });
            assertThat(lockAcquired.await(5, TimeUnit.SECONDS)).isTrue();

            var deletion = executor.submit(() -> {
                ports.as(11, Role.USER);
                try {
                    postService.deleteOwn(101);
                } finally {
                    ports.logout();
                }
                return null;
            });
            assertThatThrownBy(() -> deletion.get(200, TimeUnit.MILLISECONDS))
                .isInstanceOf(TimeoutException.class);

            release.countDown();
            holder.get(5, TimeUnit.SECONDS);
            deletion.get(5, TimeUnit.SECONDS);
            assertThat(db.queryForObject("SELECT status FROM posts WHERE id=101", String.class))
                .isEqualTo("DELETED");
        } finally {
            release.countDown();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void ratingSubmissionUpdatesOneRowAndReturnsLatestAggregate() throws Exception {
        ports.as(22, Role.USER);
        mvc.perform(put("/api/v1/posts/101/rating").contentType("application/json").content("{\"score\":5}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.score").value(5))
            .andExpect(jsonPath("$.data.rating.average").value(5.0))
            .andExpect(jsonPath("$.data.rating.count").value(1));
        long originalId = db.queryForObject("SELECT id FROM ratings WHERE post_id=101 AND user_id=22", Long.class);

        mvc.perform(put("/api/v1/posts/101/rating").contentType("application/json").content("{\"score\":1}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.rating.average").value(1.0));
        ports.as(33, Role.USER);
        mvc.perform(put("/api/v1/posts/101/rating").contentType("application/json").content("{\"score\":4}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.rating.average").value(2.5))
            .andExpect(jsonPath("$.data.rating.count").value(2));

        assertThat(db.queryForObject("SELECT COUNT(*) FROM ratings WHERE post_id=101 AND user_id=22", Integer.class))
            .isEqualTo(1);
        assertThat(db.queryForObject("SELECT id FROM ratings WHERE post_id=101 AND user_id=22", Long.class))
            .isEqualTo(originalId);
        assertThat(service.summarize(java.util.Set.of(102L)).get(102L).average()).isNull();
        assertThat(service.summarize(java.util.Set.of(102L)).get(102L).count()).isZero();
    }

    @Test
    void rejectsMalformedScoresAndEnforcesActorPermissions() throws Exception {
        ports.as(22, Role.USER);
        for (String body : List.of("{\"score\":0}", "{\"score\":6}", "{\"score\":4.5}",
                "{\"score\":\"4\"}", "{\"score\":4,\"userId\":22}")) {
            mvc.perform(put("/api/v1/posts/101/rating").contentType("application/json").content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        ports.as(11, Role.USER);
        mvc.perform(put("/api/v1/posts/101/rating").contentType("application/json").content("{\"score\":4}"))
            .andExpect(status().isForbidden());
        ports.as(44, Role.ADMIN);
        mvc.perform(put("/api/v1/posts/101/rating").contentType("application/json").content("{\"score\":4}"))
            .andExpect(status().isForbidden());
        ports.logout();
        mvc.perform(put("/api/v1/posts/101/rating").contentType("application/json").content("{\"score\":4}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void validatesAndPagesCommentsWithStableVisibilityRules() throws Exception {
        ports.as(22, Role.USER);
        mvc.perform(post("/api/v1/posts/101/comments").contentType("application/json").content("{\"content\":\" 😀 \"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.author.nickname").value("甲"))
            .andExpect(jsonPath("$.data.content").value("😀"));
        for (String body : List.of("{\"content\":\"  \\t\\n \"}",
                "{\"content\":\"" + "字".repeat(501) + "\"}")) {
            mvc.perform(post("/api/v1/posts/101/comments").contentType("application/json").content(body))
                .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/v1/posts/101/comments").contentType("application/json")
                .content("{\"content\":\"" + "😀".repeat(500) + "\"}"))
            .andExpect(status().isOk());

        db.update("INSERT INTO comments(post_id,user_id,content,status,created_at) VALUES "
            + "(101,22,'早','ACTIVE',?),(101,33,'隐藏','DELETED',?),(101,33,'中','ACTIVE',?),(101,22,'新','ACTIVE',?)",
            NOW, NOW, NOW, NOW);
        mvc.perform(get("/api/v1/posts/101/comments").param("page", "1").param("pageSize", "2"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(5))
            .andExpect(jsonPath("$.data.items.length()").value(2));
        mvc.perform(get("/api/v1/posts/101/comments").param("page", "0"))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/posts/101/comments").param("pageSize", "51"))
            .andExpect(status().isBadRequest());

        ports.as(11, Role.USER);
        mvc.perform(post("/api/v1/posts/101/comments").contentType("application/json").content("{\"content\":\"自评\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void deletedPostsAndDisabledActorsCannotInteract() throws Exception {
        ports.as(22, Role.USER);
        db.update("UPDATE posts SET status='DELETED' WHERE id=101");
        mvc.perform(put("/api/v1/posts/101/rating").contentType("application/json").content("{\"score\":4}"))
            .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/posts/101/comments").contentType("application/json").content("{\"content\":\"评论\"}"))
            .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/posts/101/comments")).andExpect(status().isNotFound());

        db.update("UPDATE posts SET status='ACTIVE' WHERE id=101");
        db.update("UPDATE users SET status='DISABLED' WHERE id=22");
        mvc.perform(put("/api/v1/posts/101/rating").contentType("application/json").content("{\"score\":4}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @Transactional
    void disabledUserCleanupRemovesRatingsAndHidesCommentsInCallerTransaction() {
        db.update("INSERT INTO ratings(post_id,user_id,score,created_at,updated_at) VALUES (101,22,4,?,?)", NOW, NOW);
        db.update("INSERT INTO comments(post_id,user_id,content,status,created_at) VALUES "
            + "(101,22,'待隐藏','ACTIVE',?),(101,33,'保留','ACTIVE',?)", NOW, NOW);

        assertThat(service.cleanupDisabledUser(22)).isEqualTo(1);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM ratings WHERE user_id=22", Integer.class)).isZero();
        assertThat(db.queryForObject("SELECT status FROM comments WHERE user_id=22", String.class)).isEqualTo("DELETED");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM comments WHERE user_id=33 AND status='ACTIVE'", Integer.class))
            .isEqualTo(1);
    }

    @Test
    void concurrentSubmissionsKeepUniqueRatingAndCorrectAggregate() throws Exception {
        int requests = 12;
        var executor = Executors.newFixedThreadPool(requests);
        var firstWave = new CountDownLatch(1);
        try {
            List<java.util.concurrent.Future<?>> sameUser = new ArrayList<>();
            for (int i = 0; i < requests; i++) {
                int score = i % 5 + 1;
                sameUser.add(executor.submit(() -> {
                    firstWave.await();
                    ports.as(22, Role.USER);
                    service.rate(101, score);
                    ports.logout();
                    return null;
                }));
            }
            firstWave.countDown();
            for (var future : sameUser) future.get(30, TimeUnit.SECONDS);
            assertThat(db.queryForObject("SELECT COUNT(*) FROM ratings WHERE post_id=101 AND user_id=22", Integer.class))
                .isEqualTo(1);

            db.update("DELETE FROM ratings");
            db.update("DELETE FROM users WHERE id > 44");
            for (int id = 100; id < 100 + requests; id++) {
                db.update("INSERT INTO users(id,nickname,role,status) VALUES (?,?,'USER','ACTIVE')", id, "并发用户" + id);
            }
            var secondWave = new CountDownLatch(1);
            List<java.util.concurrent.Future<?>> manyUsers = new ArrayList<>();
            for (int i = 0; i < requests; i++) {
                int userId = 100 + i;
                manyUsers.add(executor.submit(() -> {
                    secondWave.await();
                    ports.as(userId, Role.USER);
                    service.rate(101, 3);
                    ports.logout();
                    return null;
                }));
            }
            secondWave.countDown();
            for (var future : manyUsers) future.get(30, TimeUnit.SECONDS);
            var summary = service.summarize(java.util.Set.of(101L)).get(101L);
            assertThat(summary.average()).isEqualByComparingTo("3.0");
            assertThat(summary.count()).isEqualTo(requests);
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private static String requiredEnv(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("需通过 M04 测试脚本提供独立 MySQL 8.4 环境变量：" + key);
        }
        return value;
    }
}
