package com.shashwat.examplatformbackend.student;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(indexes = @Index(name = "idx_result_hold_registration", columnList = "registrationId"))
public class ResultHoldEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 48) private String registrationId;
    @Column(nullable = false) private boolean withheld;
    @Column(nullable = false, length = 1000) private String reason;
    @Column(nullable = false) private String actor;
    @Column(nullable = false) private Instant decidedAt;
    protected ResultHoldEvent() {}
    public ResultHoldEvent(String registrationId, boolean withheld, String reason, String actor) {
        this.registrationId = registrationId;
        this.withheld = withheld;
        this.reason = reason;
        this.actor = actor;
        this.decidedAt = Instant.now();
    }
    public Long getId() { return id; }
    public String getRegistrationId() { return registrationId; }
    public boolean isWithheld() { return withheld; }
    public String getReason() { return reason; }
    public String getActor() { return actor; }
    public Instant getDecidedAt() { return decidedAt; }
}
