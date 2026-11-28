package com.smartruble.billing.access;

import java.util.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Identity supplied by the gateway on the isolated service network. */
public record Caller(UUID id, Set<String> roles) {
  public static Caller from(HttpHeaders headers) {
    try {
      return new Caller(
          UUID.fromString(headers.getFirst("X-User-Id")),
          new HashSet<>(
              Arrays.asList(
                  Objects.requireNonNullElse(headers.getFirst("X-User-Roles"), "").split(","))));
    } catch (RuntimeException e) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Требуется вход через шлюз");
    }
  }

  public boolean has(String... names) {
    return Arrays.stream(names).anyMatch(n -> roles.contains("ROLE_" + n));
  }

  public void require(String... names) {
    if (!has(names)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Недостаточно прав");
  }
}
