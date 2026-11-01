package com.smartruble.billing.counterparties.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
@Builder
@Table(name = "counterparty_status_history")
@AllArgsConstructor
@NoArgsConstructor
public class CounterpartyStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "counterparty_id")
    private Counterparty counterparty;

    @Enumerated(EnumType.STRING)
    @Column(name = "old_status")
    private VerificationStatus oldStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status")
    private VerificationStatus newStatus;

    @Column(name = "reason")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "source")
    private StatusChangeSource source;

    @Column(name = "changed_by")
    private UUID changedBy;

    @CreationTimestamp
    @Column(name = "changed_at")
    private Instant changedAt;
}
