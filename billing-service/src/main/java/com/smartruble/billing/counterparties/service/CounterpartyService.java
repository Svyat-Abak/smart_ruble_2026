package com.smartruble.billing.counterparties.service;

import com.smartruble.billing.counterparties.entity.VerificationStatus;
import com.smartruble.billing.counterparties.web.dto.CounterpartyResponse;
import com.smartruble.billing.counterparties.web.dto.CreateCounterpartyRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public interface CounterpartyService {
    CounterpartyResponse create(CreateCounterpartyRequest request, UUID userId);
//    void validateBusinessRules(CreateCounterpartyRequest request, UUID userId);
    CounterpartyResponse getById(UUID id);
    Page<CounterpartyResponse> search(VerificationStatus status, String search, Pageable pageable);

}
