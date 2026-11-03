package com.fintrack.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;

final class Money {

    private Money() {
    }

    static String format(BigDecimal amount) {
        return NumberFormat.getCurrencyInstance(Locale.US).format(amount);
    }

    static BigDecimal percentOf(BigDecimal amount, BigDecimal percent) {
        return amount.multiply(percent).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }
}
