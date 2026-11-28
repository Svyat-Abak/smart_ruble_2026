package com.smartruble.billing.workorders;

import com.smartruble.billing.access.Caller;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/billing/workorders")
@RequiredArgsConstructor
class WorkOrderController {
  private final WorkOrders service;

  record State(@NotNull String status) {}

  record Assignee(@NotNull UUID userId) {}

  @GetMapping
  Object list(@RequestHeader HttpHeaders h) {
    return service.list(Caller.from(h));
  }

  @PostMapping
  @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  Object create(@Valid @RequestBody WorkOrders.Create req, @RequestHeader HttpHeaders h) {
    return service.create(req, Caller.from(h));
  }

  @GetMapping("/{id}")
  Object detail(@PathVariable UUID id, @RequestHeader HttpHeaders h) {
    return service.details(id, Caller.from(h));
  }

  @PostMapping("/{id}/status")
  Object status(
      @PathVariable UUID id, @Valid @RequestBody State req, @RequestHeader HttpHeaders h) {
    return service.transition(id, req.status(), Caller.from(h));
  }

  @PostMapping("/{id}/assignees")
  @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  void assign(
      @PathVariable UUID id, @Valid @RequestBody Assignee req, @RequestHeader HttpHeaders h) {
    service.assign(id, req.userId(), Caller.from(h));
  }
}
