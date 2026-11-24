package com.smartruble.billing.counterparties.repository;

import com.smartruble.billing.counterparties.entity.Counterparty;
import com.smartruble.billing.counterparties.entity.VerificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CounterpartyRepository extends JpaRepository<Counterparty, UUID> {
    boolean existsByInnAndKpp(String inn, String kpp);

    Page<Counterparty> findByVerificationStatus(VerificationStatus verificationStatus, Pageable pageable);

    Page<Counterparty> findByNameContainingIgnoreCase(String name, Pageable pageable);

    Page<Counterparty> findByVerificationStatusAndNameContainingIgnoreCase(
            VerificationStatus status, String name, Pageable pageable);

    Page<Counterparty> findByVerificationStatusNot(VerificationStatus status, Pageable pageable);

    Page<Counterparty> findByVerificationStatusNotAndNameContainingIgnoreCase(
            VerificationStatus status, String name, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Counterparty c where c.id = :id")
    Optional<Counterparty> findForUpdate(@Param("id") UUID id);
}
