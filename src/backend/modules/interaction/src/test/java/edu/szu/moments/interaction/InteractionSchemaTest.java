package edu.szu.moments.interaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import jakarta.persistence.EntityManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(classes = InteractionSchemaTest.TestApplication.class, properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.jpa.properties.hibernate.jdbc.time_zone=UTC"
})
@Transactional
class InteractionSchemaTest {
    private static final String NOW = "2026-11-23 12:00:00.123456";

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {}

    @Autowired JdbcTemplate db;
    @Autowired EntityManager entities;

    @DynamicPropertySource
    static void prepareSchema(DynamicPropertyRegistry properties) throws IOException {
        String url = testEnv("M04_TEST_DB_URL");
        String user = testEnv("M04_TEST_DB_USER");
        String password = testEnv("M04_TEST_DB_PASSWORD");
        Path versions = Files.createTempDirectory("m04-migrations-");
        Path v1 = versions.resolve("V1__test_users.sql");
        Path v2 = versions.resolve("V2__m02_posts.sql");
        Path v3 = versions.resolve("V3__m04_interaction.sql");
        try (var users = InteractionSchemaTest.class.getResourceAsStream("/fixtures/users.sql")) {
            Files.copy(users, v1);
            Files.copy(Path.of("../posts/src/main/resources/db/migration-candidates/m02/posts.sql"), v2);
            Files.copy(Path.of("src/main/resources/db/migration-candidates/m04/interaction.sql"), v3);
            var result = Flyway.configure().dataSource(url, user, password)
                .locations("filesystem:" + versions).load().migrate();
            assertThat(result.migrationsExecuted).isEqualTo(3);
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
    void seedUsersAndPost() {
        db.update("INSERT INTO users VALUES (11, '甲', 'USER', 'ACTIVE'), (22, '乙', 'USER', 'ACTIVE'), (33, '丙', 'USER', 'ACTIVE')");
        db.update("INSERT INTO posts(id, author_id, text, created_at, updated_at) VALUES (101, 11, '校园', ?, ?)", NOW, NOW);
    }

    @Test
    void scoreAndCommentRoundTripThroughJpa() {
        addRating(101, 22, 4);
        addComment(101, 22, "真好😀", "ACTIVE");
        var rating = entities.createQuery("from RatingEntity", RatingEntity.class).getSingleResult();
        var comment = entities.createQuery("from CommentEntity", CommentEntity.class).getSingleResult();
        assertThat(rating.getId()).isPositive();
        assertThat(rating.getPostId()).isEqualTo(101);
        assertThat(rating.getScore()).isEqualTo(4);
        assertThat(rating.getCreatedAt()).isEqualTo(Instant.parse("2026-11-23T12:00:00.123456Z"));
        assertThat(comment.getUserId()).isEqualTo(22);
        assertThat(comment.getContent()).isEqualTo("真好😀");
        assertThat(comment.getStatus()).isEqualTo(CommentEntity.Status.ACTIVE);
    }

    @Test
    void rejectsDuplicateRatingAndAllowsUpdatingOriginalRow() {
        addRating(101, 22, 4);
        long id = db.queryForObject("SELECT id FROM ratings", Long.class);
        assertThatThrownBy(() -> addRating(101, 22, 5)).isInstanceOf(DataAccessException.class);
        db.update("UPDATE ratings SET score = 5 WHERE post_id = 101 AND user_id = 22");
        addRating(101, 33, 4);
        assertThat(db.queryForObject("SELECT score FROM ratings WHERE id = ?", Integer.class, id)).isEqualTo(5);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM ratings WHERE post_id = 101", Integer.class)).isEqualTo(2);
    }

    @Test
    void enforcesScoreRangeAndForeignKeys() {
        assertThatThrownBy(() -> addRating(101, 22, 0)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> addRating(101, 22, 6)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> addRating(102, 22, 4)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> addRating(101, 999, 4)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> addComment(102, 22, "正常", "ACTIVE")).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> addComment(101, 999, "正常", "ACTIVE")).isInstanceOf(DataAccessException.class);
        addRating(101, 22, 1);
        addRating(101, 33, 5);
    }

    @Test
    void enforcesCommentLengthAndStatus() {
        assertThatThrownBy(() -> addComment(101, 22, "", "ACTIVE")).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> addComment(101, 22, "字".repeat(501), "ACTIVE")).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> addComment(101, 22, "正常", "HIDDEN")).isInstanceOf(DataAccessException.class);
        addComment(101, 22, "字", "ACTIVE");
        addComment(101, 22, "😀".repeat(500), "ACTIVE");
    }

    @Test
    void commentQueryCanPageStablyAndExcludeSoftDeletedRows() {
        addComment(101, 22, "最早", "ACTIVE");
        addComment(101, 33, "隐藏", "DELETED");
        addComment(101, 22, "中间", "ACTIVE");
        addComment(101, 33, "最新", "ACTIVE");
        String page = "SELECT content FROM comments WHERE post_id = 101 AND status = 'ACTIVE' "
            + "ORDER BY created_at DESC, id DESC LIMIT 2 OFFSET ?";
        assertThat(db.queryForList(page, String.class, 0)).containsExactly("最新", "中间");
        assertThat(db.queryForList(page, String.class, 2)).containsExactly("最早");
    }

    @Test
    void postSoftDeletionKeepsInteractionRowsForVisibilityCheck() {
        addRating(101, 22, 4);
        addComment(101, 22, "正常", "ACTIVE");
        db.update("UPDATE posts SET status = 'DELETED' WHERE id = 101");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM ratings WHERE post_id = 101", Integer.class)).isEqualTo(1);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM comments WHERE post_id = 101", Integer.class)).isEqualTo(1);
    }

    private void addRating(long post, long user, int score) {
        db.update("INSERT INTO ratings(post_id, user_id, score, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
            post, user, score, NOW, NOW);
    }

    private void addComment(long post, long user, String content, String status) {
        db.update("INSERT INTO comments(post_id, user_id, content, status, created_at) VALUES (?, ?, ?, ?, ?)",
            post, user, content, status, NOW);
    }

    private static String testEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("请用 scripts/verify_schema.sh 启动独立 MySQL 8.4 并执行测试。缺少 " + name);
        }
        return value;
    }
}
