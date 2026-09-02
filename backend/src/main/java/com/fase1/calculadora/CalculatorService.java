package com.fase1.calculadora;
import java.math.BigDecimal;
import java.math.RoundingMode;
public final class CalculatorService {
    public BigDecimal sum(BigDecimal a, BigDecimal b) {
        return a.add(b);
    }
    public BigDecimal subtract(BigDecimal a, BigDecimal b) {
        return a.subtract(b);
    }
    public BigDecimal multiply(BigDecimal a, BigDecimal b) {
        return a.multiply(b);
    }
    public BigDecimal divide(BigDecimal a, BigDecimal b) {
        if (b.compareTo(BigDecimal.ZERO) == 0) {
            throw new IllegalArgumentException("No se puede dividir entre cero");
        }
        return a.divide(b, 10, RoundingMode.HALF_UP).stripTrailingZeros();
    }
}
