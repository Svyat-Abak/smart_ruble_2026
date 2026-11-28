package com.smartruble.billing.counterparties.web.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.smartruble.billing.counterparties.entity.CounterpartyType;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateCounterpartyRequest {
    @NotNull
    private CounterpartyType type;
    @NotBlank @Size(max = 255)
    private String name;
    @Size(max = 255)
    private String shortName;
    @NotBlank @Pattern(regexp = "[0-9]{10}|[0-9]{12}")
    private String inn;
    @Pattern(regexp = "[0-9]{4}[A-Z0-9]{2}[0-9]{3}")
    private String kpp;
    @Pattern(regexp = "[0-9]{13}|[0-9]{15}")
    private String ogrn;
    @Size(max = 500)
    private String legalAddress;
    @JsonAlias("email")
    @Email @Size(max = 100)
    private String contactEmail;
    @Size(max = 20)
    private String contactPhone;
}
