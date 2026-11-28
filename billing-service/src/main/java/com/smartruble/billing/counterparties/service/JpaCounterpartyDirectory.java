package com.smartruble.billing.counterparties.service;

import com.smartruble.billing.counterparties.CounterpartyDirectory;
import com.smartruble.billing.counterparties.repository.CounterpartyRepository;
import com.smartruble.billing.counterparties.repository.CounterpartyMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class JpaCounterpartyDirectory implements CounterpartyDirectory {
    private final CounterpartyRepository counterparties;
    private final CounterpartyMemberRepository members;

    public Optional<Recipient> recipient(UUID id) {
        return counterparties.findById(id).map(c -> {
            var bank = c.getBankAccounts().stream().filter(a -> a.isActive() && a.isPrimary()).findFirst();
            return new Recipient(c.getId(), c.getName(), c.getInn(), c.getVerificationStatus(),
                    bank.map(a -> a.getAccountNumber()).orElse(null), bank.map(a -> a.getBik()).orElse(null));
        });
    }

    public Optional<Summary> find(UUID id) {
        return counterparties.findById(id).map(c -> new Summary(c.getId(), c.getName(), c.getVerificationStatus()));
    }

    public Optional<UUID> findCompanyOf(UUID userId) {
        return members.findByIdUserId(userId).map(member -> member.getId().getCounterpartyId());
    }
}
