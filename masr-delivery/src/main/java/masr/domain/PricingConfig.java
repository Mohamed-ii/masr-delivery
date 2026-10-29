package masr.domain;

import masr.common.Money;

import java.math.BigDecimal;

public record PricingConfig(Money baseDeliveryFee, Money perKmFee, int includedKm, BigDecimal serviceRatePercent) {
    public static PricingConfig defaults() {
        return new PricingConfig(Money.of(15), Money.of(3), 3, BigDecimal.TEN);
    }
}
