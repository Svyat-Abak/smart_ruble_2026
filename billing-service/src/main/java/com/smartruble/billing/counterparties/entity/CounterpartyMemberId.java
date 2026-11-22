package com.smartruble.billing.counterparties.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class CounterpartyMemberId implements Serializable {

    @Column(name = "counterparty_id")
    private UUID counterpartyId;

    @Column(name = "user_id")
    private UUID userId;
}
