package com.smartruble.billing.counterparties.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/** Пользователь auth-service (ROLE_CONTRACTOR), работающий от имени контрагента. */
@Entity
@Getter
@Table(name = "counterparty_members")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CounterpartyMember {

    @EmbeddedId
    private CounterpartyMemberId id;

    @MapsId("counterpartyId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "counterparty_id")
    private Counterparty counterparty;

    @Column(name = "added_by")
    private UUID addedBy;

    @CreationTimestamp
    @Column(name = "added_at")
    private Instant addedAt;

    public CounterpartyMember(Counterparty counterparty, UUID userId, UUID addedBy) {
        this.id = new CounterpartyMemberId(counterparty.getId(), userId);
        this.counterparty = counterparty;
        this.addedBy = addedBy;
    }

    public UUID getUserId() {
        return id.getUserId();
    }
}
