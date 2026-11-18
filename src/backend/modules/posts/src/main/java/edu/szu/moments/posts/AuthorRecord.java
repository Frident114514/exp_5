package edu.szu.moments.posts;

import org.hibernate.annotations.Immutable;
import jakarta.persistence.*;

/** users 表的只读投影；用户实体和迁移仍由 M01 维护。 */
@Entity(name = "PostAuthorRecord")
@Table(name = "users")
@Immutable
public class AuthorRecord {
    @Id
    private Long id;

    @Column(nullable = false)
    private String nickname;

    @Column(nullable = false)
    private String status;

    protected AuthorRecord() {}

    public Long getId() { return id; }
    public String getNickname() { return nickname; }
    public String getStatus() { return status; }
}

