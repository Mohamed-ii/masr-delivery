package masr.domain;

import masr.common.Money;
import masr.common.Validation;

import java.util.List;

/** A fixed amount off the subtotal (never more than the subtotal itself). */
public final class FixedAmountPromotion extends Promotion {
    private final Money amount;

    public FixedAmountPromotion(String code, Money amount, List<PromoCondition> conditions) {
        super(code, conditions);
        this.amount = Validation.positive(amount, "Discount amount");
    }
    @Override protected Money compute(PromoContext c) { return amount.min(c.subtotal()); }
    @Override protected String summary() { return amount + " EGP off"; }
}
