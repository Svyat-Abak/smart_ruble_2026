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
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "counterparty_bank_accounts")
public class CounterpartyBankAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "counterparty_id")
    private Counterparty counterparty;

    @Column(name = "account_number")
    private String accountNumber;

    @Column(name = "bik")
    private String bik;

    @Column(name = "bank_name")
    private String bankName;

    @Column(name = "correspondent_account")
    private String correspondentAccount;

    @Column(name = "is_primary")
    private boolean primary;

    @Column(name = "active")
    @Builder.Default
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;
}
