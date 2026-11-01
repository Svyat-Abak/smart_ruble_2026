package com.smartruble.billing.counterparties.web.dto;


import com.smartruble.billing.counterparties.entity.Counterparty;
import com.smartruble.billing.counterparties.entity.CounterpartyType;
import com.smartruble.billing.counterparties.entity.VerificationStatus;

import java.time.Instant;
import java.util.UUID;

public record CounterpartyResponse(
        UUID id,
        CounterpartyType type,
        String name,
        String shortName,
        String inn,
        String kpp,
        String ogrn,
        String legalAddress,
        String contactEmail,
        String contactPhone,
        VerificationStatus verificationStatus,
        Instant createdAt,
        Instant updatedAt,
        UUID createdBy,
        Long version
        ) {
        public static CounterpartyResponse from(Counterparty c) {
            if (c == null) {
                return null;
            }
            return new CounterpartyResponse(
                    c.getId(),
                    c.getType(),
                    c.getName(),
                    c.getShortName(),
                    c.getInn(),
                    c.getKpp(),
                    c.getOgrn(),
                    c.getLegalAddress(),
                    c.getContactEmail(),
                    c.getContactPhone(),
                    c.getVerificationStatus(),
                    c.getCreatedAt(),
                    c.getUpdatedAt(),
                    c.getCreatedBy(),
                    c.getVersion()
            );
        }
}
