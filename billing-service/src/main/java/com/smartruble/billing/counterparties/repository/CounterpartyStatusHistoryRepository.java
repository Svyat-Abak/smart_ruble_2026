package com.smartruble.billing.counterparties.repository;

import com.smartruble.billing.counterparties.entity.CounterpartyStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CounterpartyStatusHistoryRepository extends JpaRepository<CounterpartyStatusHistory, UUID> {

}
