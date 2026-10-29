package masr.domain;

import masr.common.Money;

import java.util.List;

/** Waives the delivery fee that is actually payable (after the loyalty benefit). */
public final class FreeDeliveryPromotion extends Promotion {
    public FreeDeliveryPromotion(String code, List<PromoCondition> conditions) { super(code, conditions); }
    @Override protected Money compute(PromoContext c) { return c.deliveryFee(); }
    @Override protected String summary() { return "free delivery"; }
}
