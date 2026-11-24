package com.smartruble.billing.counterparties.service;

import com.smartruble.billing.counterparties.entity.VerificationStatus;
import com.smartruble.billing.counterparties.web.dto.CounterpartyResponse;
import com.smartruble.billing.counterparties.web.dto.CreateCounterpartyRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.smartruble.billing.counterparties.web.dto.CounterpartyOperations.*;

import java.util.List;

import java.util.UUID;

public interface CounterpartyService {
    CounterpartyResponse create(CreateCounterpartyRequest request, UUID userId);
    CounterpartyResponse getById(UUID id);
    Page<CounterpartyResponse> search(VerificationStatus status, String search, Pageable pageable);

    DetailResponse getDetails(UUID id, boolean contractor);
    Page<CounterpartyResponse> search(VerificationStatus status, String search, Pageable pageable, boolean contractor);
    CounterpartyResponse update(UUID id, UpdateRequest request);
    BankAccountResponse addBankAccount(UUID id, BankAccountRequest request);
    BankAccountResponse updateBankAccount(UUID id, UUID accountId, BankAccountAction action);
    CounterpartyResponse changeStatus(UUID id, StatusRequest request, UUID userId);
    List<StatusHistoryResponse> getStatusHistory(UUID id);
    void addMember(UUID id, UUID memberId, UUID userId);
    void removeMember(UUID id, UUID memberId);
}
