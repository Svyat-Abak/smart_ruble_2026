package com.smartruble.billing.counterparties.web.controller;

import com.smartruble.billing.counterparties.service.CounterpartyService;
import com.smartruble.billing.counterparties.web.dto.CounterpartyResponse;
import com.smartruble.billing.counterparties.web.dto.CreateCounterpartyRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/billing/counterparties")
@RequiredArgsConstructor
public class CounterpartyController {

    private final CounterpartyService counterpartyService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CounterpartyResponse createCounterparty(
            @Valid @RequestBody CreateCounterpartyRequest request,
            @RequestHeader("X-User-Id") UUID userId) {
        return counterpartyService.create(request, userId);
    }
}
