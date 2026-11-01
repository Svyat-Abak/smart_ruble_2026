package com.smartruble.billing.counterparties.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Getter
@Setter
@Table(name = "counterparties")
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Counterparty {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type")
    private CounterpartyType type;

    @Column(name = "name")
    private String name;

    @Column(name = "short_name")
    private String shortName;

    @Column(name = "inn")
    private String inn;

    @Column(name = "kpp")
    private String kpp;

    @Column(name = "ogrn")
    private String ogrn;

    @Column(name = "legal_address")
    private String legalAddress;

    @Column(name = "contact_email")
    private String contactEmail;

    @Column(name = "contact_phone")
    private String contactPhone;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status")
    @Builder.Default
    private VerificationStatus verificationStatus = VerificationStatus.NEW;

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Version
    @Column(name = "version")
    private Long version;

    @OneToMany(mappedBy = "counterparty", cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @Builder.Default
    private List<CounterpartyBankAccount> bankAccounts = new ArrayList<>();

    public void addBankAccount(CounterpartyBankAccount account) {
        bankAccounts.add(account);
        account.setCounterparty(this);
    }
}
