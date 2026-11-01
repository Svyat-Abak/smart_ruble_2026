package com.smartruble.billing.counterparties.web.dto;


import com.smartruble.billing.counterparties.entity.CounterpartyType;
import com.smartruble.billing.counterparties.entity.StatusChangeSource;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

import java.util.spi.ToolProvider;

@Getter
@Setter
public class CreateCounterpartyRequest {
    @NotNull
    @Enumerated(EnumType.STRING)
    private CounterpartyType type;

    @NotBlank
    private String name;

    @NotBlank
    @Pattern(regexp = "^(\\d{10}|\\d{12})$")
    private String inn;

    @Pattern(regexp = "^\\d{9}$")
    private String kpp;

    @Email
    private String email;


}
