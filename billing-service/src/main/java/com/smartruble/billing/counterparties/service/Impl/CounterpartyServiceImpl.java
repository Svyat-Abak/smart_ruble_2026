package com.smartruble.billing.counterparties.service.Impl;

import com.smartruble.billing.counterparties.entity.*;
import com.smartruble.billing.counterparties.repository.CounterpartyRepository;
import com.smartruble.billing.counterparties.repository.CounterpartyStatusHistoryRepository;
import com.smartruble.billing.counterparties.service.CounterpartyService;
import com.smartruble.billing.counterparties.web.dto.CounterpartyResponse;
import com.smartruble.billing.counterparties.web.dto.CreateCounterpartyRequest;
import com.smartruble.billing.counterparties.repository.CounterpartyMemberRepository;
import com.smartruble.billing.counterparties.web.dto.CounterpartyOperations.*;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
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
    private final CounterpartyMemberRepository memberRepository;

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
    @Transactional(readOnly = true)
    public Page<CounterpartyResponse> search(VerificationStatus status, String search, Pageable pageable) {
        return search(status, search, pageable, false);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CounterpartyResponse> search(VerificationStatus status, String search, Pageable pageable,
                                            boolean contractor) {
        if (contractor && status == VerificationStatus.BLOCKED) {
            return Page.empty(pageable);
        }
        boolean byName = search != null && !search.isBlank();
        Page<Counterparty> result;
        if (status != null) {
            result = byName ? counterpartyRepository.findByVerificationStatusAndNameContainingIgnoreCase(status, search, pageable)
                    : counterpartyRepository.findByVerificationStatus(status, pageable);
        } else if (contractor) {
            result = byName ? counterpartyRepository.findByVerificationStatusNotAndNameContainingIgnoreCase(
                    VerificationStatus.BLOCKED, search, pageable)
                    : counterpartyRepository.findByVerificationStatusNot(VerificationStatus.BLOCKED, pageable);
        } else {
            result = byName ? counterpartyRepository.findByNameContainingIgnoreCase(search, pageable)
                    : counterpartyRepository.findAll(pageable);
        }
        return result.map(CounterpartyResponse::from);
    }

    @Override
    @Transactional(readOnly = true)
    public DetailResponse getDetails(UUID id, boolean contractor) {
        Counterparty counterparty = find(id);
        if (contractor && counterparty.getVerificationStatus() == VerificationStatus.BLOCKED) {
            throw notFound();
        }
        return new DetailResponse(CounterpartyResponse.from(counterparty),
                counterparty.getBankAccounts().stream().map(BankAccountResponse::from).toList());
    }

    @Override
    @Transactional
    public CounterpartyResponse update(UUID id, UpdateRequest request) {
        Counterparty counterparty = lock(id);
        if (counterparty.getType() != CounterpartyType.LEGAL_ENTITY && request.kpp() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "КПП может быть указан только для юридических лиц");
        }
        if (counterparty.getType() == CounterpartyType.SELF_EMPLOYED && request.ogrn() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "У самозанятого не должно быть ОГРН");
        }
        if (!Objects.equals(counterparty.getKpp(), request.kpp())
                && counterpartyRepository.existsByInnAndKpp(counterparty.getInn(), request.kpp())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Контрагент с таким ИНН и КПП уже существует");
        }
        counterparty.setName(request.name());
        counterparty.setShortName(request.shortName());
        counterparty.setKpp(request.kpp());
        counterparty.setOgrn(request.ogrn());
        counterparty.setLegalAddress(request.legalAddress());
        counterparty.setContactEmail(request.contactEmail());
        counterparty.setContactPhone(request.contactPhone());
        counterpartyRepository.flush();
        return CounterpartyResponse.from(counterparty);
    }

    @Override
    @Transactional
    public BankAccountResponse addBankAccount(UUID id, BankAccountRequest request) {
        Counterparty counterparty = lock(id);
        if (counterparty.getBankAccounts().stream().anyMatch(account ->
                account.getBik().equals(request.bik()) && account.getAccountNumber().equals(request.accountNumber()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Банковский счёт уже существует");
        }
        CounterpartyBankAccount account = CounterpartyBankAccount.builder()
                .accountNumber(request.accountNumber()).bik(request.bik()).bankName(request.bankName())
                .correspondentAccount(request.correspondentAccount())
                .primary(counterparty.getBankAccounts().stream().noneMatch(CounterpartyBankAccount::isActive))
                .build();
        counterparty.addBankAccount(account);
        counterpartyRepository.flush();
        return BankAccountResponse.from(account);
    }

    @Override
    @Transactional
    public BankAccountResponse updateBankAccount(UUID id, UUID accountId, BankAccountAction action) {
        Counterparty counterparty = lock(id);
        CounterpartyBankAccount account = counterparty.getBankAccounts().stream()
                .filter(candidate -> candidate.getId().equals(accountId)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Банковский счёт не найден"));
        if (action == BankAccountAction.DEACTIVATE) {
            if (account.isPrimary() && account.isActive()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Сначала выберите другой основной счёт");
            }
            account.setActive(false);
        } else if (!account.isActive()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Неактивный счёт нельзя сделать основным");
        } else if (!account.isPrimary()) {
            counterparty.getBankAccounts().forEach(candidate -> candidate.setPrimary(false));
            // Частичный уникальный индекс проверяется при каждом UPDATE.
            counterpartyRepository.flush();
            account.setPrimary(true);
        }
        return BankAccountResponse.from(account);
    }

    @Override
    @Transactional
    public CounterpartyResponse changeStatus(UUID id, StatusRequest request, UUID userId) {
        Counterparty counterparty = lock(id);
        VerificationStatus previous = counterparty.getVerificationStatus();
        if (previous == request.status()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Новый статус совпадает с текущим");
        }
        if ((request.status() == VerificationStatus.BLOCKED || request.status() == VerificationStatus.SUSPICIOUS)
                && (request.reason() == null || request.reason().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Укажите причину смены статуса");
        }
        counterparty.setVerificationStatus(request.status());
        historyRepository.save(CounterpartyStatusHistory.builder()
                .counterparty(counterparty).oldStatus(previous).newStatus(request.status())
                .reason(request.reason()).source(StatusChangeSource.MANUAL).changedBy(userId).build());
        counterpartyRepository.flush();
        return CounterpartyResponse.from(counterparty);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StatusHistoryResponse> getStatusHistory(UUID id) {
        find(id);
        return historyRepository.findByCounterpartyIdOrderByChangedAtDesc(id)
                .stream().map(StatusHistoryResponse::from).toList();
    }

    @Override
    @Transactional
    public void addMember(UUID id, UUID memberId, UUID userId) {
        Counterparty counterparty = lock(id);
        if (counterparty.getVerificationStatus() == VerificationStatus.BLOCKED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Контрагент заблокирован");
        }
        if (memberRepository.existsByIdUserId(memberId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Пользователь уже привязан к контрагенту");
        }
        memberRepository.save(new CounterpartyMember(counterparty, memberId, userId));
    }

    @Override
    @Transactional
    public void removeMember(UUID id, UUID memberId) {
        lock(id);
        CounterpartyMember member = memberRepository.findById(new CounterpartyMemberId(id, memberId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Участник не найден"));
        memberRepository.delete(member);
    }

    private Counterparty find(UUID id) {
        return counterpartyRepository.findById(id).orElseThrow(this::notFound);
    }

    private Counterparty lock(UUID id) {
        return counterpartyRepository.findForUpdate(id).orElseThrow(this::notFound);
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Контрагент не найден");
    }
}
