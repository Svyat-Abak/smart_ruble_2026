package com.smartruble.billing.counterparties.repository;

import com.smartruble.billing.counterparties.entity.Counterparty;
import com.smartruble.billing.counterparties.entity.VerificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CounterpartyRepository extends JpaRepository<Counterparty, UUID> {
    boolean existsByInnAndKpp(String inn, String kpp);

    Page<Counterparty> findByVerificationStatus(VerificationStatus verificationStatus, Pageable pageable);

    Page<Counterparty> findByNameContainingIgnoreCase(String name, Pageable pageable);
}
