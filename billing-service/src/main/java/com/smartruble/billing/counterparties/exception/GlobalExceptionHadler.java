package com.smartruble.billing.counterparties.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@RestControllerAdvice(basePackages = "com.smartruble.billing.counterparties.web")
public class GlobalExceptionHadler {

    private static final Set<String> UNIQUE_CONSTRAINTS = Set.of(
            "ux_counterparties_inn_kpp", "ux_cp_bank_accounts_account",
            "ux_cp_bank_accounts_primary", "ux_cp_members_user", "pk_counterparty_members");

    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ResponseEntity<ApiError> handleStatus(org.springframework.web.server.ResponseStatusException ex,
                                                  HttpServletRequest request) {
        return error(ex.getStatusCode(), ex.getReason(), request, Map.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(field -> fields.putIfAbsent(field.getField(), field.getDefaultMessage()));
        return error(HttpStatus.BAD_REQUEST, "Ошибка валидации", request, fields);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation && violation.getConstraintName() != null
                    && UNIQUE_CONSTRAINTS.contains(violation.getConstraintName())) {
                return error(HttpStatus.CONFLICT, "Запись конфликтует с существующими данными", request, Map.of());
            }
        }
        throw ex;
    }

    private ResponseEntity<ApiError> error(HttpStatusCode code, String message, HttpServletRequest request,
                                           Map<String, String> fields) {
        HttpStatus status = HttpStatus.resolve(code.value());
        String reason = status == null ? "HTTP error" : status.getReasonPhrase();
        return ResponseEntity.status(code)
                .body(new ApiError(Instant.now(), code.value(), reason, message, request.getRequestURI(), fields));
    }

    public record ApiError(Instant timestamp, int status, String error, String message,
                           String path, Map<String, String> fieldErrors) {}
}
