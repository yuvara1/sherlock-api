package in.sherlock.auth.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
public class AuditLog {
    @Id
    private UUID id;
    private UUID userId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AuditEvent event;
    @Column(nullable = false, length = 64)
    private String ipAddress;
    @Column(nullable = false, length = 500)
    private String userAgent;
    @Column(nullable = false)
    private Instant timestamp;

    protected AuditLog() {}

    public AuditLog(UUID userId, AuditEvent event, String ipAddress, String userAgent) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.event = event;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.timestamp = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public AuditEvent getEvent() { return event; }
    public String getIpAddress() { return ipAddress; }
    public String getUserAgent() { return userAgent; }
    public Instant getTimestamp() { return timestamp; }
}
