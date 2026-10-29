package masr.common;

import masr.exception.InvalidInputException;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Immutable EGP amount stored as a whole number of piastres (1 EGP = 100 piastres).
 * Integer arithmetic is exact; every place that can produce a fraction of a piastre
 * (percentages, per-kg pricing) rounds explicitly with HALF_UP through BigDecimal.
 */
public final class Money implements Comparable<Money> {
    public static final Money ZERO = new Money(0L);

    private final long piastres;

    private Money(long piastres) { this.piastres = piastres; }

    public static Money ofPiastres(long piastres) { return new Money(piastres); }
    public static Money of(long egp) { return new Money(Math.multiplyExact(egp, 100L)); }
    public static Money of(BigDecimal egp) {
        return new Money(egp.setScale(2, RoundingMode.HALF_UP).unscaledValue().longValueExact());
    }
    public static Money of(String egp) {
        try {
            return of(new BigDecimal(egp.trim()));
        } catch (NumberFormatException | NullPointerException e) {
            throw new InvalidInputException("'" + egp + "' is not a valid amount");
        }
    }

    public long piastres() { return piastres; }
    public Money plus(Money o) { return new Money(Math.addExact(piastres, o.piastres)); }
    public Money minus(Money o) { return new Money(Math.subtractExact(piastres, o.piastres)); }
    public Money times(long n) { return new Money(Math.multiplyExact(piastres, n)); }
    /** Multiply by a decimal factor (e.g. kilograms), rounded HALF_UP to the nearest piastre. */
    public Money times(BigDecimal factor) {
        return new Money(BigDecimal.valueOf(piastres).multiply(factor)
                .setScale(0, RoundingMode.HALF_UP).longValueExact());
    }
    /** pct is expressed in percent: 10 means 10%. Rounded HALF_UP to the nearest piastre. */
    public Money percent(BigDecimal pct) { return times(pct.movePointLeft(2)); }
    public Money min(Money o) { return compareTo(o) <= 0 ? this : o; }
    public Money max(Money o) { return compareTo(o) >= 0 ? this : o; }
    public boolean isNegative() { return piastres < 0; }
    public boolean isZero() { return piastres == 0; }
    public BigDecimal toBigDecimal() { return BigDecimal.valueOf(piastres, 2); }

    @Override public int compareTo(Money o) { return Long.compare(piastres, o.piastres); }
    @Override public boolean equals(Object o) { return o instanceof Money m && m.piastres == piastres; }
    @Override public int hashCode() { return Long.hashCode(piastres); }
    @Override public String toString() {
        long abs = Math.abs(piastres);
        return (piastres < 0 ? "-" : "") + (abs / 100) + "." + String.format("%02d", abs % 100);
    }
}
