package com.smartruble.billing.counterparties.repository;

import com.smartruble.billing.counterparties.entity.CounterpartyMember;
import com.smartruble.billing.counterparties.entity.CounterpartyMemberId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CounterpartyMemberRepository extends JpaRepository<CounterpartyMember, CounterpartyMemberId> {
    Optional<CounterpartyMember> findByIdUserId(UUID userId);
    boolean existsByIdUserId(UUID userId);
}
