package masr.domain;

import masr.common.Money;
import masr.common.Validation;

import java.math.BigDecimal;
import java.util.List;

/** Percentage off the subtotal, never more than the cap. */
public final class PercentagePromotion extends Promotion {
    private final BigDecimal percent;
    private final Money cap;

    public PercentagePromotion(String code, BigDecimal percent, Money cap, List<PromoCondition> conditions) {
        super(code, conditions);
        this.percent = Validation.positive(percent, "Percentage");
        if (percent.compareTo(BigDecimal.valueOf(100)) > 0)
            throw new masr.exception.InvalidInputException("Percentage cannot exceed 100");
        this.cap = Validation.positive(cap, "Maximum discount");
    }
    @Override protected Money compute(PromoContext c) { return c.subtotal().percent(percent).min(cap).min(c.subtotal()); }
    @Override protected String summary() { return percent.stripTrailingZeros().toPlainString() + "% off, max " + cap + " EGP"; }
}
