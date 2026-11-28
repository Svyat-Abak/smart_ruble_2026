package com.smartruble.billing.counterparties.web.controller;

import com.smartruble.billing.counterparties.service.CounterpartyService;
import com.smartruble.billing.counterparties.web.dto.CounterpartyResponse;
import com.smartruble.billing.counterparties.web.dto.CreateCounterpartyRequest;
import com.smartruble.billing.counterparties.VerificationStatus;
import com.smartruble.billing.counterparties.web.dto.CounterpartyOperations.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/billing/counterparties")
@RequiredArgsConstructor
public class CounterpartyController {

    private static final Set<String> EMPLOYEES = Set.of(
            "ROLE_EMPLOYEE", "ROLE_MANAGER", "ROLE_FINANCE_DIRECTOR", "ROLE_ADMIN");
    private static final Set<String> WRITERS = Set.of("ROLE_MANAGER", "ROLE_FINANCE_DIRECTOR", "ROLE_ADMIN");
    private static final Set<String> APPROVERS = Set.of("ROLE_FINANCE_DIRECTOR", "ROLE_ADMIN");
    private static final Set<String> MEMBER_MANAGERS = Set.of("ROLE_MANAGER", "ROLE_ADMIN");

    private final CounterpartyService counterpartyService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CounterpartyResponse createCounterparty(@Valid @RequestBody CreateCounterpartyRequest request,
                                                   @RequestHeader HttpHeaders headers) {
        return counterpartyService.create(request, caller(headers).require(WRITERS).id());
    }

    @GetMapping
    public PagedModel<CounterpartyResponse> search(@RequestParam(required = false) VerificationStatus status,
                                                  @RequestParam(required = false) String search,
                                                  Pageable pageable, @RequestHeader HttpHeaders headers) {
        Page<CounterpartyResponse> page = counterpartyService.search(status, search, pageable,
                caller(headers).isContractorOnly());
        return new PagedModel<>(page);
    }

    @GetMapping("/{id}")
    public DetailResponse getById(@PathVariable UUID id, @RequestHeader HttpHeaders headers) {
        return counterpartyService.getDetails(id, caller(headers).isContractorOnly());
    }

    @PutMapping("/{id}")
    public CounterpartyResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateRequest request,
                                       @RequestHeader HttpHeaders headers) {
        caller(headers).require(WRITERS);
        return counterpartyService.update(id, request);
    }

    @PostMapping("/{id}/bank-accounts")
    @ResponseStatus(HttpStatus.CREATED)
    public BankAccountResponse addBankAccount(@PathVariable UUID id, @Valid @RequestBody BankAccountRequest request,
                                               @RequestHeader HttpHeaders headers) {
        caller(headers).require(WRITERS);
        return counterpartyService.addBankAccount(id, request);
    }

    @PatchMapping("/{id}/bank-accounts/{accountId}")
    public BankAccountResponse updateBankAccount(@PathVariable UUID id, @PathVariable UUID accountId,
                                                  @Valid @RequestBody BankAccountUpdate request,
                                                  @RequestHeader HttpHeaders headers) {
        caller(headers).require(WRITERS);
        return counterpartyService.updateBankAccount(id, accountId, request.action());
    }

    @PostMapping("/{id}/status")
    public CounterpartyResponse changeStatus(@PathVariable UUID id, @Valid @RequestBody StatusRequest request,
                                              @RequestHeader HttpHeaders headers) {
        return counterpartyService.changeStatus(id, request, caller(headers).require(APPROVERS).id());
    }

    @GetMapping("/{id}/status-history")
    public List<StatusHistoryResponse> statusHistory(@PathVariable UUID id, @RequestHeader HttpHeaders headers) {
        caller(headers).require(EMPLOYEES);
        return counterpartyService.getStatusHistory(id);
    }

    @PostMapping("/{id}/members")
    @ResponseStatus(HttpStatus.CREATED)
    public void addMember(@PathVariable UUID id, @Valid @RequestBody MemberRequest request,
                          @RequestHeader HttpHeaders headers) {
        counterpartyService.addMember(id, request.userId(), caller(headers).require(MEMBER_MANAGERS).id());
    }

    @DeleteMapping("/{id}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(@PathVariable UUID id, @PathVariable UUID userId, @RequestHeader HttpHeaders headers) {
        caller(headers).require(MEMBER_MANAGERS);
        counterpartyService.removeMember(id, userId);
    }

    private static Caller caller(HttpHeaders headers) {
        String userId = headers.getFirst("X-User-Id");
        if (userId == null || userId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Требуется аутентификация");
        }
        UUID id;
        try {
            id = UUID.fromString(userId);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Некорректный идентификатор пользователя");
        }
        String rawRoles = headers.getFirst("X-User-Roles");
        Set<String> roles = rawRoles == null ? Set.of() : Arrays.stream(rawRoles.split(","))
                .map(String::trim).collect(Collectors.toUnmodifiableSet());
        return new Caller(id, roles);
    }

    private record Caller(UUID id, Set<String> roles) {
        Caller require(Set<String> allowedRoles) {
            if (roles.stream().noneMatch(allowedRoles::contains)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Недостаточно прав");
            }
            return this;
        }

        boolean isContractorOnly() {
            if (roles.stream().anyMatch(EMPLOYEES::contains)) {
                return false;
            }
            require(Set.of("ROLE_CONTRACTOR"));
            return true;
        }
    }
}
