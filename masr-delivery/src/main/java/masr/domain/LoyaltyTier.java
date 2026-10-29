package masr.domain;

import masr.common.Money;

import java.math.BigDecimal;

/** Derived from the completed-order count; never set by hand. */
public enum LoyaltyTier {
    BRONZE(0, 0, "none"),
    SILVER(10, 10, "10% off the delivery fee"),
    GOLD(30, 100, "delivery fee waived");

    private final int minCompleted;
    private final int feeDiscountPercent;
    private final String benefit;

    LoyaltyTier(int minCompleted, int feeDiscountPercent, String benefit) {
        this.minCompleted = minCompleted;
        this.feeDiscountPercent = feeDiscountPercent;
        this.benefit = benefit;
    }

    public static LoyaltyTier forCompleted(int completedOrders) {
        LoyaltyTier best = BRONZE;
        for (LoyaltyTier t : values()) if (completedOrders >= t.minCompleted) best = t;
        return best;
    }
    public Money applyToDeliveryFee(Money fee) {
        return fee.minus(fee.percent(BigDecimal.valueOf(feeDiscountPercent)));
    }
    public String benefit() { return benefit; }
}
