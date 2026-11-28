package com.smartruble.billing.counterparties.exception;

public class InvalidRequisite extends RuntimeException {
    private final String field;

    public InvalidRequisite(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
