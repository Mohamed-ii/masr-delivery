package masr.domain;

import masr.common.Money;

public record PriceBreakdown(Money subtotal, Money deliveryFee, Money serviceFee, Money discount, Money total,
                             String promoCode) { }
