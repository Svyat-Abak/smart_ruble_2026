package com.smartruble.billing.counterparties.web.dto;

import com.smartruble.billing.counterparties.entity.CounterpartyBankAccount;
import com.smartruble.billing.counterparties.entity.CounterpartyStatusHistory;
import com.smartruble.billing.counterparties.entity.StatusChangeSource;
import com.smartruble.billing.counterparties.VerificationStatus;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class CounterpartyOperations {
    private CounterpartyOperations() {}

    public record UpdateRequest(
            @NotBlank @Size(max = 255) String name,
            @Size(max = 255) String shortName,
            @Pattern(regexp = "[0-9]{9}|[0-9]{4}[A-Z0-9]{2}[0-9]{3}") String kpp,
            @Pattern(regexp = "[0-9]{13}|[0-9]{15}") String ogrn,
            @Size(max = 500) String legalAddress,
            @Email @Size(max = 100) String contactEmail,
            @Size(max = 20) String contactPhone) {}

    public record BankAccountRequest(
            @NotNull @Pattern(regexp = "[0-9]{20}") String accountNumber,
            @NotNull @Pattern(regexp = "[0-9]{9}") String bik,
            @NotBlank @Size(max = 255) String bankName,
            @Pattern(regexp = "[0-9]{20}") String correspondentAccount) {}

    public enum BankAccountAction { MAKE_PRIMARY, DEACTIVATE }

    public record BankAccountUpdate(@NotNull BankAccountAction action) {}

    public record BankAccountResponse(UUID id, String accountNumber, String bik, String bankName,
                                      String correspondentAccount, boolean primary, boolean active) {
        public static BankAccountResponse from(CounterpartyBankAccount account) {
            return new BankAccountResponse(account.getId(), account.getAccountNumber(), account.getBik(),
                    account.getBankName(), account.getCorrespondentAccount(), account.isPrimary(), account.isActive());
        }
    }

    public record DetailResponse(CounterpartyResponse counterparty, List<BankAccountResponse> bankAccounts) {}

    public record StatusRequest(@NotNull VerificationStatus status, @Size(max = 1000) String reason) {}

    public record StatusHistoryResponse(UUID id, VerificationStatus oldStatus, VerificationStatus newStatus,
                                        String reason, StatusChangeSource source, UUID changedBy, Instant changedAt) {
        public static StatusHistoryResponse from(CounterpartyStatusHistory history) {
            return new StatusHistoryResponse(history.getId(), history.getOldStatus(), history.getNewStatus(),
                    history.getReason(), history.getSource(), history.getChangedBy(), history.getChangedAt());
        }
    }

    public record MemberRequest(@NotNull UUID userId) {}
}
