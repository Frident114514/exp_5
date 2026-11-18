package edu.szu.moments.posts;

import jakarta.persistence.*;

@Entity(name = "MomentTag")
@Table(name = "tags")
public class TagEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String name;

    protected TagEntity() {}

    public Long getId() { return id; }
    public String getName() { return name; }
}

