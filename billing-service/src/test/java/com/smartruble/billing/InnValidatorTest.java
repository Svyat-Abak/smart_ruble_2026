package com.smartruble.billing;

import static org.assertj.core.api.Assertions.*;

import com.smartruble.billing.counterparties.service.InnValidator;
import org.junit.jupiter.api.Test;

class InnValidatorTest {
  @Test
  void validatesBothFormatsAndRejectsAlteredDigits() {
    assertThat(InnValidator.valid("7707083893")).isTrue();
    assertThat(InnValidator.valid("500100732259")).isTrue();
    assertThat(InnValidator.valid("7707083894")).isFalse();
    assertThat(InnValidator.valid("500100732258")).isFalse();
    assertThat(InnValidator.valid("0000000000")).isFalse();
    assertThat(InnValidator.valid("abcdefghij")).isFalse();
  }
}
