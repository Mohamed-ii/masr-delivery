package masr.domain;

import masr.common.Money;
import masr.exception.PromotionException;
import masr.exception.PromotionExpiredException;
import masr.exception.PromotionNotApplicableException;

import java.time.LocalDate;

public final class PromoConditions {
    private PromoConditions() { }

    public static PromoCondition minSubtotal(Money min) {
        return new PromoCondition() {
            public void check(PromoContext c) throws PromotionException {
                if (c.subtotal().compareTo(min) < 0)
                    throw new PromotionNotApplicableException("This promotion needs a subtotal of at least "
                            + min + " EGP; yours is " + c.subtotal() + " EGP");
            }
            public String describe() { return "minimum subtotal " + min + " EGP"; }
        };
    }
    /** Valid through the given date, inclusive. */
    public static PromoCondition expiresOn(LocalDate lastDay) {
        return new PromoCondition() {
            public void check(PromoContext c) throws PromotionException {
                if (c.now().toLocalDate().isAfter(lastDay))
                    throw new PromotionExpiredException("This promotion expired on " + lastDay);
            }
            public String describe() { return "valid until " + lastDay; }
        };
    }
    public static PromoCondition onlyInDistrict(District d) {
        return new PromoCondition() {
            public void check(PromoContext c) throws PromotionException {
                if (c.deliveryDistrict() != d)
                    throw new PromotionNotApplicableException("This promotion only applies to deliveries in " + d);
            }
            public String describe() { return "deliveries to " + d + " only"; }
        };
    }
    public static PromoCondition firstTimeCustomersOnly() {
        return new PromoCondition() {
            public void check(PromoContext c) throws PromotionException {
                if (!c.firstTimeCustomer())
                    throw new PromotionNotApplicableException("This promotion is for first-time customers only");
            }
            public String describe() { return "first-time customers only"; }
        };
    }
}
