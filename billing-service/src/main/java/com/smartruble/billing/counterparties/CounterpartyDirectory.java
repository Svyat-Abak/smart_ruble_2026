package com.smartruble.billing.counterparties;

import java.util.Optional;
import java.util.UUID;

public interface CounterpartyDirectory {
    record Summary(UUID id, String name, VerificationStatus status) {}

    record Recipient(UUID id, String name, String inn, VerificationStatus status, String account, String bik) {}
    Optional<Recipient> recipient(UUID id);
    Optional<Summary> find(UUID counterpartyId);
    Optional<UUID> findCompanyOf(UUID userId);
}
