package com.smartruble.billing.counterparties.service.Impl;

import com.smartruble.billing.counterparties.entity.*;
import com.smartruble.billing.counterparties.repository.CounterpartyRepository;
import com.smartruble.billing.counterparties.repository.CounterpartyStatusHistoryRepository;
import com.smartruble.billing.counterparties.service.CounterpartyService;
import com.smartruble.billing.counterparties.web.dto.CounterpartyResponse;
import com.smartruble.billing.counterparties.web.dto.CreateCounterpartyRequest;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
@AllArgsConstructor
public class CounterpartyServiceImpl implements CounterpartyService {

    private final CounterpartyRepository counterpartyRepository;
    private final CounterpartyStatusHistoryRepository historyRepository;

    @Override
    @Transactional
    public CounterpartyResponse create(CreateCounterpartyRequest request, UUID userId) {
        validateBusinessRules(request, userId);

        if (counterpartyRepository.existsByInnAndKpp(request.getInn(), request.getKpp())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot create counterparty because counterparty already exists");
        }

        Counterparty counterparty = Counterparty.builder()
                .type(request.getType())
                .name(request.getName())
                .inn(request.getInn())
                .kpp(request.getKpp())
                .contactEmail(request.getEmail())
                .verificationStatus(VerificationStatus.NEW)
                .createdBy(userId)
                .build();

        counterparty = counterpartyRepository.save(counterparty);

//        CounterpartyStatusHistory history = new CounterpartyStatusHistory();
//        history.setCounterparty(counterparty);
//        history.getOldStatus(null);
//        history.setNewStatus(VerificationStatus.NEW);
//        history.setSource(StatusChangeSource.MANUAL);
//        history.setChangedBy(userId);

        CounterpartyStatusHistory history = CounterpartyStatusHistory.builder()
                .counterparty(counterparty)
                .oldStatus(null) // Передаем null легально
                .newStatus(VerificationStatus.NEW)
                .source(StatusChangeSource.MANUAL)
                .changedBy(userId)
                .build();

        historyRepository.save(history);

        return CounterpartyResponse.from(counterparty);
    }


    private void validateBusinessRules(CreateCounterpartyRequest request, UUID userId) {
        String type = request.getType().name();
        String inn = request.getInn();
//        String name = request.getName();
        String kpp = request.getKpp();
//        String email = request.getEmail();

        if (request.getType() == CounterpartyType.LEGAL_ENTITY && inn.length() != 10) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ИНН юридического лица должен состоять из 10 цифр");
        }
        if (("INDIVIDUAL_ENTREPRENEUR".equals(type) || "SELF_EMPLOYED".equals(type)) && inn.length() != 12) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ИНН ИП или самозанятого должен состоять из 12 цифр");
        }
        if (!"LEGAL_ENTITY".equals(type) && kpp != null && !kpp.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "КПП может быть указан только для юридических лиц");
        }
    }

    @Override
    public CounterpartyResponse getById(UUID id) {
        return counterpartyRepository.findById(id)
                .map(CounterpartyResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Контрагент не найден"));
    }

    @Override
    public Page<CounterpartyResponse> search(VerificationStatus status, String search, Pageable pageable) {
        Page<Counterparty> counterparties;

        if (status != null) {
            counterparties = counterpartyRepository.findByVerificationStatus(status, pageable);
        } else if (search != null && !search.isBlank()) {
            counterparties = counterpartyRepository.findByNameContainingIgnoreCase(search, pageable);
        } else {
            counterparties = counterpartyRepository.findAll(pageable);
        }
        return counterparties.map(CounterpartyResponse::from);
    }
}
