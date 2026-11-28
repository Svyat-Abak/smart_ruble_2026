package com.smartruble.billing.counterparties.service;

public final class InnValidator {
  private InnValidator() {}

  public static boolean valid(String inn) {
    if (inn == null || !inn.matches("[0-9]{10}|[0-9]{12}") || inn.chars().allMatch(c -> c == '0'))
      return false;
    if (inn.length() == 10)
      return digit(inn, new int[] {2, 4, 10, 3, 5, 9, 4, 6, 8}) == inn.charAt(9) - '0';
    return digit(inn, new int[] {7, 2, 4, 10, 3, 5, 9, 4, 6, 8}) == inn.charAt(10) - '0'
        && digit(inn, new int[] {3, 7, 2, 4, 10, 3, 5, 9, 4, 6, 8}) == inn.charAt(11) - '0';
  }

  private static int digit(String value, int[] weights) {
    int sum = 0;
    for (int i = 0; i < weights.length; i++) sum += (value.charAt(i) - '0') * weights[i];
    return sum % 11 % 10;
  }
}
