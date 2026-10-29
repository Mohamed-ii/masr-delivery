package masr.domain;

import masr.common.Money;
import masr.exception.PromotionException;

import java.time.LocalDateTime;
import java.util.List;

/** Part B, in exactly the stated order: subtotal, delivery fee (+loyalty), service fee, promotion, total. */
public final class PricingService {
    private final PricingConfig cfg;

    public PricingService(PricingConfig cfg) { this.cfg = cfg; }

    public PriceBreakdown price(District restaurantDistrict, District deliveryDistrict, List<OrderLine> lines,
                                LoyaltyTier tier, Promotion promo, boolean firstTimeCustomer, LocalDateTime now)
            throws PromotionException {
        Money subtotal = lines.stream().map(OrderLine::total).reduce(Money.ZERO, Money::plus);

        int extraKm = Math.max(0, restaurantDistrict.distanceKmTo(deliveryDistrict) - cfg.includedKm());
        Money deliveryFee = tier.applyToDeliveryFee(cfg.baseDeliveryFee().plus(cfg.perKmFee().times(extraKm)));

        Money serviceFee = subtotal.percent(cfg.serviceRatePercent());

        Money discount = Money.ZERO;
        String code = null;
        if (promo != null) {
            discount = promo.discountFor(new PromoContext(subtotal, deliveryFee, deliveryDistrict,
                    firstTimeCustomer, now));
            code = promo.code();
        }
        Money total = subtotal.plus(deliveryFee).plus(serviceFee).minus(discount).max(Money.ZERO);
        return new PriceBreakdown(subtotal, deliveryFee, serviceFee, discount, total, code);
    }
}
