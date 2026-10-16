package com.smartruble.apigateway.filter;

import org.springframework.core.Ordered;

/** Порядок глобальных фильтров: чем меньше число, тем раньше фильтр выполняется. */
public final class FilterOrder {
    public static final int REQUEST_ID = Ordered.HIGHEST_PRECEDENCE;
    public static final int RATE_LIMIT = Ordered.HIGHEST_PRECEDENCE + 10;
    public static final int USER_HEADERS = Ordered.HIGHEST_PRECEDENCE + 20;

    private FilterOrder() {
    }
}
