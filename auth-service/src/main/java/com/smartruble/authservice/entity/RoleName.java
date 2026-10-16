package com.smartruble.authservice.entity;

/**
 * Роли приложения. Значения должны совпадать с записями таблицы roles
 * (см. db/changelog/1.0/002-insert-roles.xml).
 * Префикс ROLE_ нужен Spring Security для hasRole('...').
 */
public enum RoleName {
    ROLE_EMPLOYEE,
    ROLE_MANAGER,
    ROLE_FINANCE_DIRECTOR,
    ROLE_ADMIN
}
