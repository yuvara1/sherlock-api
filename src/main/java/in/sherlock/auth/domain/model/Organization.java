package in.sherlock.auth.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "organizations")
public class Organization {
    @Id
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 120)
    private String slug;

    @Column(nullable = false, length = 20)
    private String plan;

    @Column(nullable = false)
    private Instant createdAt;

    protected Organization() {}

    public Organization(String name, String slug) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.slug = slug;
        this.plan = "FREE";
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getSlug() { return slug; }
    public String getPlan() { return plan; }
    public Instant getCreatedAt() { return createdAt; }
}
